#!/usr/bin/env python3
"""
Crea (o actualiza) el webhook de PUSH en los repos de GitLab que se listen en REPOSITORIES.

El webhook llama al job orquestador de Jenkins (push_orchestrator), que lanza el pipeline de la rama.
  - Nombre      : WEBHOOK_NAME (descriptivo, se ve en Settings -> Webhooks de cada repo)
  - Eventos     : solo "Push events" (todas las ramas)
  - Secret token: el mismo password de la credencial de Jenkins 'gitlab-webhook-token'
  - SSL         : "Enable SSL verification" DESACTIVADO
Si el repo ya tiene un webhook con la misma URL, lo actualiza (no lo duplica).

Uso (solo librería estándar de Python 3, sin pip install):
  1. Editar JENKINS_WEBHOOK_URL y REPOSITORIES abajo.
  2. Ejecutar:  python3 create_push_webhook.py
     Pide el token de GitLab y el secret token (no se muestran al escribir). También se pueden pasar como
     variables de entorno GITLAB_API_TOKEN y WEBHOOK_SECRET_TOKEN.
     Con --dry-run solo muestra lo que haría, sin cambiar nada.

Token de GitLab: Personal/Project/Group Access Token con scope 'api' de un usuario con rol Maintainer en los repos.
Repo público: NO escribir aquí tokens, secret tokens ni URLs internas reales que no deban publicarse.
"""

import getpass
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

# ---------------------------------------------------------------------------------------------------------------------
# CONFIGURACIÓN (editar)
# ---------------------------------------------------------------------------------------------------------------------

# URL del job orquestador en Jenkins (GitLab Plugin): https://<jenkins>/project/<carpeta>/<job>
JENKINS_WEBHOOK_URL = 'https://jenkins.ejemplo.com/project/webhook/push-in-application-repository'

# Nombre y descripción del webhook en GitLab
WEBHOOK_NAME        = 'Jenkins - Push (orquestador de pipelines)'
WEBHOOK_DESCRIPTION = 'Cada push lanza el job orquestador de Jenkins, que ejecuta el pipeline de la rama.'

# Repos donde se crea el webhook: URL completa del repo en GitLab (igual que en el navegador, con o sin .git)
REPOSITORIES = [
    'https://gitlab.com.co/pais/codigo/repo1',
    # 'https://gitlab.com.co/pais/codigo/repo2',
]

# ---------------------------------------------------------------------------------------------------------------------

WEBHOOK_SETTINGS = {
    'push_events': True,
    'tag_push_events': False,
    'merge_requests_events': False,
    'issues_events': False,
    'confidential_issues_events': False,
    'note_events': False,
    'confidential_note_events': False,
    'job_events': False,
    'pipeline_events': False,
    'wiki_page_events': False,
    'deployment_events': False,
    'releases_events': False,
    'enable_ssl_verification': False,
}


def split_repository_url(repository_url):
    """'https://gitlab.com.co/pais/codigo/repo1(.git)' -> ('https://gitlab.com.co', 'pais/codigo/repo1')"""
    parsed = urllib.parse.urlparse(repository_url.strip())
    if parsed.scheme not in ('http', 'https') or not parsed.netloc:
        raise ValueError(f'URL de repo no válida (debe empezar con https://): {repository_url}')
    path = parsed.path.strip('/')
    if path.endswith('.git'):
        path = path[:-4]
    if path.count('/') < 1:
        raise ValueError(f'La URL no tiene grupo/proyecto: {repository_url}')
    return f'{parsed.scheme}://{parsed.netloc}', path


def gitlab_request(method, url, api_token, data=None):
    """Llama a la API de GitLab y devuelve el JSON de la respuesta. Lanza RuntimeError con el código y mensaje si falla."""
    body = None
    headers = {'PRIVATE-TOKEN': api_token}
    if data is not None:
        body = json.dumps(data).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            text = response.read().decode('utf-8')
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        message = error.read().decode('utf-8', errors='replace')
        raise RuntimeError(f'HTTP {error.code}: {message}') from None
    except urllib.error.URLError as error:
        raise RuntimeError(f'No se pudo conectar: {error.reason}') from None


def configure_webhook(repository_url, api_token, secret_token, dry_run):
    server, project_path = split_repository_url(repository_url)
    hooks_api = f'{server}/api/v4/projects/{urllib.parse.quote(project_path, safe="")}/hooks'

    hooks = gitlab_request('GET', hooks_api, api_token) or []
    existing = next((hook for hook in hooks if hook.get('url') == JENKINS_WEBHOOK_URL), None)

    data = dict(WEBHOOK_SETTINGS)
    data.update({
        'url': JENKINS_WEBHOOK_URL,
        'name': WEBHOOK_NAME,
        'description': WEBHOOK_DESCRIPTION,
        'token': secret_token,
    })

    action = f'actualizar (id {existing["id"]})' if existing else 'crear'
    if dry_run:
        print(f'[INFO]: (dry-run) {project_path}: se va a {action} el webhook "{WEBHOOK_NAME}"')
        return

    if existing:
        gitlab_request('PUT', f'{hooks_api}/{existing["id"]}', api_token, data)
    else:
        gitlab_request('POST', hooks_api, api_token, data)
    print(f'[INFO]: ✅ {project_path}: webhook "{WEBHOOK_NAME}" {"actualizado" if existing else "creado"}')


def main():
    dry_run = '--dry-run' in sys.argv[1:]

    if not REPOSITORIES:
        sys.exit('[ERROR]: ❌ REPOSITORIES está vacío: agrega las URL de los repos.')

    api_token = os.environ.get('GITLAB_API_TOKEN') or getpass.getpass('Token de GitLab (scope api): ')
    secret_token = os.environ.get('WEBHOOK_SECRET_TOKEN') or getpass.getpass('Secret token del webhook: ')
    if not api_token.strip() or not secret_token.strip():
        sys.exit('[ERROR]: ❌ Falta el token de GitLab o el secret token.')

    print(f'[INFO]: Webhook de push → {JENKINS_WEBHOOK_URL} ({len(REPOSITORIES)} repo(s))')
    failed = []
    for repository_url in REPOSITORIES:
        try:
            configure_webhook(repository_url, api_token.strip(), secret_token.strip(), dry_run)
        except (ValueError, RuntimeError) as error:
            print(f'[ERROR]: ❌ {repository_url}: {error}')
            failed.append(repository_url)

    print(f'[INFO]: Resumen: {len(REPOSITORIES) - len(failed)} correcto(s), {len(failed)} con error')
    if failed:
        print('[INFO]: 401 = token inválido; 403 = sin rol Maintainer o sin scope api; 404 = repo no existe o sin acceso.')
        sys.exit(1)


if __name__ == '__main__':
    main()
