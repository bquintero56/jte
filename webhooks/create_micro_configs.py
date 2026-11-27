#!/usr/bin/env python3
"""
Crea en el repo de configuraciones JTE de GitLab el archivo default-integration-config.groovy de cada micro de MICROS.

  - Contenido: plantilla webhooks/templates/default-integration-config.groovy (la misma para todos los micros); solo cambia
    __COMPONENT_NAME__ por el nombre del micro (= nombre del repo, = project key de SonarQube).
  - Ruta: CONFIG_PATH_TEMPLATE (ej. jte-pipeline-configurations-automations/jboss-micro/{appcode}/{micro}/pipeline_configurations/...)
  - Si el archivo YA EXISTE en la rama no se toca (no se sobrescribe); solo se crean los que faltan.
  - Todos los archivos nuevos van en UN commit (API /repository/commits):
      CREATE_MERGE_REQUEST = False → commit directo a TARGET_BRANCH
      CREATE_MERGE_REQUEST = True  → commit en una rama nueva + Merge Request hacia TARGET_BRANCH (si la rama está protegida)

Uso (solo librería estándar de Python 3, sin pip install):
  1. Editar la CONFIGURACIÓN de abajo (GitLab, proyecto de configuraciones, rama, ruta, APPCODE y MICROS).
  2. Ejecutar:  python3 create_micro_configs.py            (o --dry-run: solo muestra lo que haría, sin cambiar nada)
     Token de GitLab (scope api, rol Developer o superior en el proyecto): campo GITLAB_API_TOKEN, variable de entorno con el
     mismo nombre, o se pide al ejecutar. Proxy: PROXY_URL abajo (o variable de entorno GITLAB_PROXY_URL); usuario: PROXY_USER.

Repo público: NO hacer commit de este archivo con tokens ni URLs internas reales.
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

# URL base de GitLab, INCLUYENDO la subruta si GitLab no está en la raíz del dominio (ej. 'https://servidor.ejemplo.com/gitlab')
GITLAB_URL = 'https://gitlab.ejemplo.com'

# Token de GitLab (scope api). Vacío = variable de entorno GITLAB_API_TOKEN o se pide al ejecutar.
# ⚠️ Si lo escribes aquí, NO hagas commit de este archivo con el valor: el repo es público.
GITLAB_API_TOKEN = ''

# Proxy para llegar a GitLab (ej. 'http://proxy.empresa.com:8080'), SIN usuario ni clave. Vacío = HTTPS_PROXY del entorno o sin proxy.
PROXY_URL = ''

# Proyecto de GitLab con las configuraciones JTE (ruta grupo/proyecto, la de la URL sin GITLAB_URL ni .git)
CONFIG_PROJECT = 'grupo/pipelines'

# Rama donde deben quedar las configs (la que usan los jobs de Jenkins)
TARGET_BRANCH = 'main'

# False = commit directo a TARGET_BRANCH. True = rama nueva + Merge Request (usar si TARGET_BRANCH está protegida).
CREATE_MERGE_REQUEST = False

# Ruta del archivo en el repo. Variables: {appcode}, {micro}
CONFIG_PATH_TEMPLATE = 'jte-pipeline-configurations-automations/jboss-micro/{appcode}/{micro}/pipeline_configurations/default-integration-config.groovy'

# Plantilla del contenido (junto a este script). Marcador: __COMPONENT_NAME__ → nombre del micro
TEMPLATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'templates', 'default-integration-config.groovy')

# Appcode de todos los micros de esta corrida (una corrida = un appcode)
APPCODE = 'appcode'

# Micros: solo el nombre (= nombre del repo en GitLab)
MICROS = [
    'micro1',
    # 'micro2',
]

# ---------------------------------------------------------------------------------------------------------------------

OPENER = None


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


def gitlab_request(method, url, api_token, data=None, allow_404=False):
    """Llama a la API de GitLab y devuelve el JSON (o None si 404 y allow_404). Lanza RuntimeError si falla."""
    body = None
    headers = {'PRIVATE-TOKEN': api_token}
    if data is not None:
        body = json.dumps(data).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with OPENER.open(request, timeout=30) as response:
            text = response.read().decode('utf-8')
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as error:
        if error.code == 404 and allow_404:
            return None
        message = error.read().decode('utf-8', errors='replace')[:500]
        raise RuntimeError(f'HTTP {error.code} en {method} {url.split("?")[0]}: {message}') from None
    except urllib.error.URLError as error:
        raise RuntimeError(f'No se pudo conectar a {url.split("?")[0]}: {error.reason}') from None


def file_exists(project_api, file_path, branch, api_token):
    url = f'{project_api}/repository/files/{urllib.parse.quote(file_path, safe="")}?ref={urllib.parse.quote(branch, safe="")}'
    return gitlab_request('GET', url, api_token, allow_404=True) is not None


def main():
    global OPENER
    dry_run = '--dry-run' in sys.argv[1:]

    appcode = APPCODE.strip().strip('/')
    if not appcode or '/' in appcode:
        sys.exit(f'[ERROR]: ❌ APPCODE no válido: \'{APPCODE}\' (solo el nombre, ej. \'appcode\').')
    if not MICROS:
        sys.exit('[ERROR]: ❌ MICROS está vacío: agrega los nombres de los micros.')
    if not os.path.isfile(TEMPLATE_FILE):
        sys.exit(f'[ERROR]: ❌ No existe la plantilla {TEMPLATE_FILE}')
    with open(TEMPLATE_FILE, encoding='utf-8') as template_file:
        template = template_file.read()
    if '__COMPONENT_NAME__' not in template:
        sys.exit('[ERROR]: ❌ La plantilla no tiene el marcador __COMPONENT_NAME__.')

    api_token = (GITLAB_API_TOKEN or os.environ.get('GITLAB_API_TOKEN') or getpass.getpass('Token de GitLab (scope api): ')).strip()
    if not api_token:
        sys.exit('[ERROR]: ❌ Falta el token de GitLab.')

    OPENER = build_opener()
    server = GITLAB_URL.strip().rstrip('/')
    project = CONFIG_PROJECT.strip().strip('/')
    if project.endswith('.git'):
        project = project[:-4]
    project_api = f'{server}/api/v4/projects/{urllib.parse.quote(project, safe="")}'

    print(f'[INFO]: GitLab: {server} | proyecto: {project} | rama: {TARGET_BRANCH} | appcode: {appcode} | micros: {len(MICROS)}'
          f'{" | DRY-RUN (no se cambia nada)" if dry_run else ""}')

    # 1. Archivos a crear (los que no existen)
    actions, micros_new, existing, failed = [], [], 0, []
    for micro_path in MICROS:
        micro = micro_path.strip().strip('/')
        if not micro or '/' in micro:
            print(f'[ERROR]: ❌ \'{micro_path}\': pon solo el nombre del micro (el appcode va en APPCODE)')
            failed.append(micro_path)
            continue
        file_path = CONFIG_PATH_TEMPLATE.format(appcode=appcode, micro=micro)
        try:
            if file_exists(project_api, file_path, TARGET_BRANCH, api_token):
                print(f'[INFO]: {file_path}: ya existía (no se modificó)')
                existing += 1
                continue
        except RuntimeError as error:
            print(f'[ERROR]: ❌ {micro_path}: {error}')
            failed.append(micro_path)
            continue
        actions.append({'action': 'create', 'file_path': file_path, 'content': template.replace('__COMPONENT_NAME__', micro)})
        micros_new.append(micro)
        print(f'[INFO]: {file_path}: {"se va a crear (dry-run)" if dry_run else "se crea"}')

    # 2. Un solo commit con todos los archivos nuevos (directo o por Merge Request)
    if actions and not dry_run:
        message = f'Configs JTE (default-integration-config.groovy) de: {", ".join(micros_new)}'
        commit = {'branch': TARGET_BRANCH, 'commit_message': message, 'actions': actions}
        if CREATE_MERGE_REQUEST:
            new_branch = 'jte-configs-' + '-'.join(micros_new)[:80]
            commit.update({'branch': new_branch, 'start_branch': TARGET_BRANCH})
        try:
            gitlab_request('POST', f'{project_api}/repository/commits', api_token, commit)
            print(f'[INFO]: ✅ Commit en la rama {commit["branch"]}: {message}')
            if CREATE_MERGE_REQUEST:
                mr = gitlab_request('POST', f'{project_api}/merge_requests', api_token, {
                    'source_branch': commit['branch'], 'target_branch': TARGET_BRANCH, 'title': message,
                    'remove_source_branch': True,
                })
                print(f'[INFO]: ✅ Merge Request creado: {mr.get("web_url", "")}')
        except RuntimeError as error:
            print(f'[ERROR]: ❌ No se pudo hacer el commit: {error}')
            if 'protected' in str(error).lower() or 'HTTP 403' in str(error):
                print(f'[INFO]: Si la rama {TARGET_BRANCH} está protegida, usa CREATE_MERGE_REQUEST = True.')
            failed.extend(micros_new)
            micros_new = []

    created = len(actions) if dry_run else len(micros_new)
    print(f'[INFO]: Resumen: archivos {"a crear" if dry_run else "creados"} {created}, ya existían {existing}, con error {len(failed)}')
    if failed:
        print('[INFO]: 401 = token inválido; 403 = sin permiso o rama protegida; 404 = proyecto no existe (revisa GITLAB_URL y CONFIG_PROJECT).')
        sys.exit(1)


if __name__ == '__main__':
    main()
