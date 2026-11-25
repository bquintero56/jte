#!/usr/bin/env python3
"""
Crea (o actualiza) el webhook de PUSH en los repos de GitLab que se listen en REPOSITORIES.

El webhook llama al job orquestador de Jenkins (push_orchestrator), que lanza el pipeline de la rama.
  - Nombre      : WEBHOOK_NAME (descriptivo, se ve en Settings -> Webhooks de cada repo)
  - Eventos     : solo "Push events", opción "All branches"
  - Secret token: el mismo password de la credencial de Jenkins 'gitlab-webhook-token'
  - SSL         : "Enable SSL verification" DESACTIVADO
Si el repo ya tiene un webhook con la misma URL, lo actualiza (no lo duplica).

Uso (solo librería estándar de Python 3, sin pip install):
  1. Editar JENKINS_WEBHOOK_URL y REPOSITORIES abajo.
  2. Ejecutar:  python3 create_push_webhook.py
     Pide el token de GitLab y el secret token (no se muestran al escribir). También se pueden pasar como
     variables de entorno GITLAB_API_TOKEN y WEBHOOK_SECRET_TOKEN.
     Con --dry-run solo muestra lo que haría, sin cambiar nada.
     Proxy: PROXY_URL abajo (o variable de entorno GITLAB_PROXY_URL); si pide usuario, variable de entorno PROXY_USER.

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

# URL base de GitLab, INCLUYENDO la ruta si GitLab no está en la raíz del dominio (ej. 'https://servidor.ejemplo.com/gitlab').
# Vacío = GitLab en la raíz: la base es https://<dominio> de cada repo. La API queda en <GITLAB_URL>/api/v4.
GITLAB_URL = ''

# Repos donde se crea el webhook: URL completa del repo en GitLab (igual que en el navegador, con o sin .git)
REPOSITORIES = [
    'https://gitlab.com.co/pais/codigo/repo1',
    # 'https://gitlab.com.co/pais/codigo/repo2',
]

# Proxy para llegar a GitLab (ej. 'http://proxy.empresa.com:8080'), SIN usuario ni clave. Vacío = sin proxy propio: se usan las
# variables de entorno HTTPS_PROXY / HTTP_PROXY si existen. Si el proxy pide usuario, definir PROXY_USER (variable de entorno):
# la clave se pide al ejecutar (o variable de entorno PROXY_PASSWORD).
PROXY_URL = ''

# Secret token del webhook (= password de la credencial de Jenkins 'gitlab-webhook-token'). Vacío = se toma de la variable de entorno
# WEBHOOK_SECRET_TOKEN o se pide al ejecutar. ⚠️ Si lo escribes aquí, NO hagas commit de este archivo con el valor: el repo es público.
WEBHOOK_SECRET_TOKEN = ''

# Token de GitLab (scope api, usuario con rol Maintainer en los repos). Vacío = se toma de la variable de entorno GITLAB_API_TOKEN o se
# pide al ejecutar. ⚠️ Si lo escribes aquí, NO hagas commit de este archivo con el valor: el repo es público.
GITLAB_API_TOKEN = ''

# ---------------------------------------------------------------------------------------------------------------------

WEBHOOK_SETTINGS = {
    'push_events': True,
    'branch_filter_strategy': 'all_branches',   # Push events → "All branches" (también al actualizar un webhook que tenía filtro)
    'push_events_branch_filter': '',
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
    """Devuelve (URL base de GitLab, ruta del proyecto).
    Con GITLAB_URL = 'https://servidor.ejemplo.com/gitlab':
        'https://servidor.ejemplo.com/gitlab/pais/codigo/repo1(.git)' -> ('https://servidor.ejemplo.com/gitlab', 'pais/codigo/repo1')
    Sin GITLAB_URL (GitLab en la raíz del dominio):
        'https://gitlab.com.co/pais/codigo/repo1(.git)'  -> ('https://gitlab.com.co', 'pais/codigo/repo1')"""
    url = repository_url.strip().rstrip('/')
    if url.endswith('.git'):
        url = url[:-4]
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ('http', 'https') or not parsed.netloc:
        raise ValueError(f'URL de repo no válida (debe empezar con https://): {repository_url}')

    gitlab_url = GITLAB_URL.strip().rstrip('/')
    if gitlab_url:
        if not url.startswith(gitlab_url + '/'):
            raise ValueError(f'La URL del repo no empieza con GITLAB_URL ({gitlab_url}): {repository_url}')
        server, path = gitlab_url, url[len(gitlab_url) + 1:]
    else:
        server, path = f'{parsed.scheme}://{parsed.netloc}', parsed.path.strip('/')

    if path.count('/') < 1:
        raise ValueError(f'La URL no tiene grupo/proyecto: {repository_url}')
    return server, path


def build_opener():
    """Cliente HTTP: con PROXY_URL (y usuario/clave si PROXY_USER está definido) o, si está vacío, con el proxy del entorno."""
    proxy_url = (os.environ.get('GITLAB_PROXY_URL') or PROXY_URL).strip()
    if not proxy_url:
        env_proxy = urllib.request.getproxies().get('https')
        print(f'[INFO]: Proxy: {"del entorno (HTTPS_PROXY)" if env_proxy else "sin proxy"}')
        return urllib.request.build_opener()

    parsed = urllib.parse.urlparse(proxy_url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname:
        sys.exit(f'[ERROR]: ❌ PROXY_URL no válida (ej. http://proxy.empresa.com:8080): {proxy_url}')
    if parsed.username:
        sys.exit('[ERROR]: ❌ No pongas usuario ni clave en PROXY_URL: usa la variable de entorno PROXY_USER (la clave se pide al ejecutar).')

    proxy_user = os.environ.get('PROXY_USER', '').strip()
    if proxy_user:
        proxy_password = os.environ.get('PROXY_PASSWORD') or getpass.getpass(f'Clave del proxy para {proxy_user}: ')
        credentials = f'{urllib.parse.quote(proxy_user, safe="")}:{urllib.parse.quote(proxy_password, safe="")}@'
        proxy_with_auth = f'{parsed.scheme}://{credentials}{parsed.netloc}'
    else:
        proxy_with_auth = proxy_url

    print(f'[INFO]: Proxy: {parsed.scheme}://{parsed.netloc}{" (con usuario " + proxy_user + ")" if proxy_user else ""}')
    return urllib.request.build_opener(urllib.request.ProxyHandler({'http': proxy_with_auth, 'https': proxy_with_auth}))


OPENER = None


def gitlab_request(method, url, api_token, data=None):
    """Llama a la API de GitLab y devuelve el JSON de la respuesta. Lanza RuntimeError con el código y mensaje si falla."""
    body = None
    headers = {'PRIVATE-TOKEN': api_token}
    if data is not None:
        body = json.dumps(data).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with OPENER.open(request, timeout=30) as response:
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
    global OPENER
    dry_run = '--dry-run' in sys.argv[1:]

    if not REPOSITORIES:
        sys.exit('[ERROR]: ❌ REPOSITORIES está vacío: agrega las URL de los repos.')

    api_token = GITLAB_API_TOKEN or os.environ.get('GITLAB_API_TOKEN') or getpass.getpass('Token de GitLab (scope api): ')
    secret_token = WEBHOOK_SECRET_TOKEN or os.environ.get('WEBHOOK_SECRET_TOKEN') or getpass.getpass('Secret token del webhook: ')
    if not api_token.strip() or not secret_token.strip():
        sys.exit('[ERROR]: ❌ Falta el token de GitLab o el secret token.')

    OPENER = build_opener()

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
