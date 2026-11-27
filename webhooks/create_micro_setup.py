#!/usr/bin/env python3
"""
Da de alta UN micro completo, en este orden:

  1. CONFIG    : crea en el repo de configuraciones de GitLab (CONFIG_PROJECT) el archivo
                 .../jboss-micro/<appcode>/<micro>/pipeline_configurations/default-integration-config.groovy
                 a partir de la plantilla templates/default-integration-config.groovy (__COMPONENT_NAME__ → nombre del micro).
  2. WEBHOOKS  : en el repo del micro (<pais>/<appcode>/<micro> en GitLab) crea o actualiza los webhooks de Push (orquestador) y
                 de Merge Request (validador), con Secret token y "Enable SSL verification" desactivado.
  3. PIPELINES : crea en Jenkins <pais>/<appcode>/<micro>/{integration, dev, qa, uat} con la definición JTE (repo de configuraciones +
                 archivo de config de cada job) y la GitLab connection.

  - Lo que YA EXISTE no se sobrescribe: el archivo de config y los jobs/carpetas de Jenkins se saltan. Los webhooks con la misma URL se
    actualizan (no se duplican).
  - Antes de cambiar nada valida que existan el repo del micro y el de configuraciones en GitLab y que el usuario de Jenkins sirva.
  - Si un paso falla, se detiene y no hace los siguientes.

Uso (solo librería estándar de Python 3, sin pip install):
  1. Editar la CONFIGURACIÓN de abajo una sola vez (GitLab, Jenkins, rutas, PAIS y APPCODE).
  2. Ejecutar con el nombre del micro (= nombre del repo en GitLab):
        python3 create_micro_setup.py micro1 --dry-run      (solo muestra lo que haría, sin cambiar nada)
        python3 create_micro_setup.py micro1
     Sin nombre, toma MICRO de abajo o lo pide al ejecutar.
     Tokens: campos de abajo, variables de entorno con el mismo nombre, o se piden al ejecutar.
     Proxy: PROXY_URL abajo (vacío = HTTPS_PROXY del entorno); si pide usuario, variable de entorno PROXY_USER.

Repo público: NO hacer commit de este archivo con tokens, usuarios ni URLs internas reales.
"""

import getpass
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from base64 import b64encode
from xml.sax.saxutils import escape

# ---------------------------------------------------------------------------------------------------------------------
# CONFIGURACIÓN (editar)
# ---------------------------------------------------------------------------------------------------------------------

# --- Micro ---
PAIS = 'pais'                 # primera carpeta en GitLab y en Jenkins
APPCODE = 'appcode'           # segunda carpeta en GitLab y en Jenkins
MICRO = ''                    # vacío = se pasa como argumento o se pide al ejecutar

# Ruta del repo del micro en GitLab (la de la URL sin GITLAB_URL ni .git). Variables: {pais}, {appcode}, {micro}
MICRO_PROJECT_TEMPLATE = '{pais}/{appcode}/{micro}'

# --- GitLab ---
# URL base de GitLab, INCLUYENDO la subruta si GitLab no está en la raíz del dominio (ej. 'https://servidor.ejemplo.com/gitlab')
GITLAB_URL = 'https://gitlab.ejemplo.com'

# Token de GitLab (scope api, rol Maintainer en el repo del micro y Developer o superior en el de configuraciones).
# Vacío = variable de entorno GITLAB_API_TOKEN o se pide al ejecutar. ⚠️ NO hagas commit de este archivo con el valor.
GITLAB_API_TOKEN = ''

# Proxy (ej. 'http://proxy.empresa.com:8080'), SIN usuario ni clave. Se usa para GitLab y Jenkins. Vacío = HTTPS_PROXY del entorno.
PROXY_URL = ''

# --- 1. Config en el repo de configuraciones ---
CONFIG_PROJECT = 'grupo/pipelines'          # ruta del repo de configuraciones (sin GITLAB_URL ni .git)
TARGET_BRANCH = 'main'                      # rama de las configs (la que usan los jobs de Jenkins)
CREATE_MERGE_REQUEST = False                # True = rama nueva + Merge Request (si TARGET_BRANCH está protegida)

# Ruta del archivo de config dentro del repo (la MISMA para el paso 1 y para los jobs del paso 3).
# Variables: {pais}, {appcode}, {micro}, {config} (prefijo del archivo, ver PIPELINES)
CONFIG_PATH_TEMPLATE = 'jte-pipeline-configurations-automations/jboss-micro/{appcode}/{micro}/pipeline_configurations/{config}-integration-config.groovy'

# Plantilla del contenido (junto a este script). Marcador: __COMPONENT_NAME__ → nombre del micro
TEMPLATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'templates', 'default-integration-config.groovy')

# --- 2. Webhooks en el repo del micro ---
PUSH_WEBHOOK_URL = 'https://jenkins.ejemplo.com/project/webhook/push-in-application-repository'
PUSH_WEBHOOK_NAME = 'Jenkins - Push (orquestador de pipelines)'
PUSH_WEBHOOK_DESCRIPTION = 'Cada push lanza el job orquestador de Jenkins, que ejecuta el pipeline de la rama.'

MERGE_REQUEST_WEBHOOK_URL = 'https://jenkins.ejemplo.com/project/webhook/merge-request-validator'
MERGE_REQUEST_WEBHOOK_NAME = 'Jenkins - Merge Request (validador de promoción)'
MERGE_REQUEST_WEBHOOK_DESCRIPTION = 'Cada MR abierto o actualizado lanza el validador de Jenkins: cierra los MR que no siguen dev -> qa -> uat -> main.'

# Secret token de los webhooks (= password de la credencial de Jenkins 'gitlab-webhook-token').
# Vacío = variable de entorno WEBHOOK_SECRET_TOKEN o se pide al ejecutar. ⚠️ NO hagas commit de este archivo con el valor.
WEBHOOK_SECRET_TOKEN = ''

# --- 3. Pipelines en Jenkins ---
JENKINS_URL = 'https://jenkins.ejemplo.com'     # sin / final

# Usuario y API token de Jenkins. Vacíos = variables de entorno JENKINS_USER / JENKINS_API_TOKEN o se piden al ejecutar.
JENKINS_USER = ''
JENKINS_API_TOKEN = ''

# Repo de configuraciones como lo clona Jenkins. Vacío = <GITLAB_URL>/<CONFIG_PROJECT>.git
CONFIG_REPO_URL = ''
CONFIG_REPO_CREDENTIALS_ID = 'GITLAB_CLONE'     # credencial de Jenkins para clonar ese repo
CONFIG_REPO_BRANCH = ''                         # vacío = */<TARGET_BRANCH>

# GitLab connection del GitLab Plugin (Manage Jenkins → System → GitLab → Connection name). Vacío = sin GitLab connection.
GITLAB_CONNECTION = 'gitlab'

# Campo "Pipeline Template Path" del job (el template real lo elige la config con pipeline_template)
PIPELINE_TEMPLATE_PATH = 'Jenkinsfile'

# Jobs dentro de la carpeta del micro: nombre del job → prefijo de su archivo de config ({config}-integration-config.groovy)
PIPELINES = {
    'integration': 'default',
    'dev': 'dev',
    'qa': 'qa',
    'uat': 'uat',
}

# ---------------------------------------------------------------------------------------------------------------------

PUSH_WEBHOOK_SETTINGS = {
    'push_events': True,
    'branch_filter_strategy': 'all_branches',
    'push_events_branch_filter': '',
    'merge_requests_events': False,
}

MERGE_REQUEST_WEBHOOK_SETTINGS = {
    'push_events': False,
    'merge_requests_events': True,
}

COMMON_WEBHOOK_SETTINGS = {
    'tag_push_events': False,
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

FOLDER_XML = """<?xml version='1.1' encoding='UTF-8'?>
<com.cloudbees.hudson.plugins.folder.Folder>
  <description></description>
  <properties/>
</com.cloudbees.hudson.plugins.folder.Folder>
"""

JOB_XML = """<?xml version='1.1' encoding='UTF-8'?>
<flow-definition>
  <description>{description}</description>
  <keepDependencies>false</keepDependencies>
  <properties>{gitlab_connection_property}</properties>
  <definition class="org.boozallen.plugins.jte.job.AdHocTemplateFlowDefinition">
    <configProvider class="org.boozallen.plugins.jte.job.ScmAdHocTemplateFlowDefinitionConfiguration">
      <scm class="hudson.plugins.git.GitSCM">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>{repo_url}</url>
            <credentialsId>{credentials_id}</credentialsId>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>{branch}</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="empty-list"/>
        <extensions/>
      </scm>
      <pipelineConfigurationPath>{config_path}</pipelineConfigurationPath>
      <pipelineTemplatePath>{template_path}</pipelineTemplatePath>
    </configProvider>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
"""

GITLAB_CONNECTION_XML = """
    <com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty plugin="gitlab-plugin">
      <gitLabConnection>{connection}</gitLabConnection>
      <useAlternativeCredential>false</useAlternativeCredential>
    </com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty>
  """

OPENER = None
GITLAB_TOKEN = ''
JENKINS_AUTH = ''


# --- HTTP ------------------------------------------------------------------------------------------------------------

def build_opener():
    """Cliente HTTP: con PROXY_URL (y usuario/clave si PROXY_USER está definido) o, si está vacío, con el proxy del entorno."""
    proxy_url = PROXY_URL.strip()
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


def http_request(method, url, headers, body=None):
    """Devuelve (código, texto). 404 se devuelve como código; los demás errores lanzan RuntimeError."""
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with OPENER.open(request, timeout=30) as response:
            return response.status, response.read().decode('utf-8', errors='replace')
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return 404, ''
        message = error.read().decode('utf-8', errors='replace')[:500]
        raise RuntimeError(f'HTTP {error.code} en {method} {url.split("?")[0]}: {message}') from None
    except urllib.error.URLError as error:
        raise RuntimeError(f'No se pudo conectar a {url.split("?")[0]}: {error.reason}') from None


def gitlab(method, url, data=None):
    """API de GitLab: devuelve el JSON, o None si responde 404."""
    headers = {'PRIVATE-TOKEN': GITLAB_TOKEN}
    body = None
    if data is not None:
        body = json.dumps(data).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    status, text = http_request(method, url, headers, body)
    if status == 404:
        return None
    return json.loads(text) if text else {}


def jenkins(method, url, xml=None):
    """API de Jenkins: devuelve el código HTTP (404 incluido)."""
    headers = {'Authorization': JENKINS_AUTH}
    body = None
    if xml is not None:
        body = xml.encode('utf-8')
        headers['Content-Type'] = 'application/xml; charset=utf-8'
    status, _ = http_request(method, url, headers, body)
    return status


def gitlab_project_api(project_path):
    return f'{GITLAB_URL.strip().rstrip("/")}/api/v4/projects/{urllib.parse.quote(project_path, safe="")}'


def jenkins_job_url(path_parts):
    """['pais', 'appcode', 'micro1'] → https://jenkins/job/pais/job/appcode/job/micro1"""
    return JENKINS_URL.strip().rstrip('/') + ''.join(f'/job/{urllib.parse.quote(part)}' for part in path_parts)


def clean_project(path):
    path = path.strip().strip('/')
    return path[:-4] if path.endswith('.git') else path


# --- Paso 1: config --------------------------------------------------------------------------------------------------

def create_config(micro, dry_run):
    print('[INFO]: ---------- 1. Config en el repo de configuraciones ----------')
    project = clean_project(CONFIG_PROJECT)
    project_api = gitlab_project_api(project)
    file_path = CONFIG_PATH_TEMPLATE.format(pais=PAIS, appcode=APPCODE, micro=micro, config=PIPELINES.get('integration', 'default'))

    file_url = (f'{project_api}/repository/files/{urllib.parse.quote(file_path, safe="")}'
                f'?ref={urllib.parse.quote(TARGET_BRANCH, safe="")}')
    if gitlab('GET', file_url) is not None:
        print(f'[INFO]: {project}:{TARGET_BRANCH} {file_path}: ya existía (no se modificó)')
        return 'ya existía'

    with open(TEMPLATE_FILE, encoding='utf-8') as template_file:
        content = template_file.read().replace('__COMPONENT_NAME__', micro)

    if dry_run:
        print(f'[INFO]: (dry-run) {project}:{TARGET_BRANCH} {file_path}: se va a crear')
        return 'se va a crear'

    message = f'Config JTE (default-integration-config.groovy) de {micro}'
    commit = {'branch': TARGET_BRANCH, 'commit_message': message,
              'actions': [{'action': 'create', 'file_path': file_path, 'content': content}]}
    if CREATE_MERGE_REQUEST:
        commit.update({'branch': f'jte-config-{micro}'[:100], 'start_branch': TARGET_BRANCH})

    try:
        gitlab('POST', f'{project_api}/repository/commits', commit)
    except RuntimeError as error:
        if 'protected' in str(error).lower() or 'HTTP 403' in str(error):
            print(f'[INFO]: Si la rama {TARGET_BRANCH} está protegida, usa CREATE_MERGE_REQUEST = True.')
        raise
    print(f'[INFO]: ✅ Commit en la rama {commit["branch"]}: {file_path}')

    if CREATE_MERGE_REQUEST:
        mr = gitlab('POST', f'{project_api}/merge_requests', {
            'source_branch': commit['branch'], 'target_branch': TARGET_BRANCH, 'title': message, 'remove_source_branch': True,
        }) or {}
        print(f'[INFO]: ✅ Merge Request creado (apruébalo antes de correr los pipelines): {mr.get("web_url", "")}')
        return 'MR creado'
    return 'creado'


# --- Paso 2: webhooks ------------------------------------------------------------------------------------------------

def create_webhooks(micro_project, secret_token, dry_run):
    print('[INFO]: ---------- 2. Webhooks en el repo del micro ----------')
    hooks_api = f'{gitlab_project_api(micro_project)}/hooks'
    hooks = gitlab('GET', hooks_api) or []

    results = []
    for url, name, description, settings in (
        (PUSH_WEBHOOK_URL, PUSH_WEBHOOK_NAME, PUSH_WEBHOOK_DESCRIPTION, PUSH_WEBHOOK_SETTINGS),
        (MERGE_REQUEST_WEBHOOK_URL, MERGE_REQUEST_WEBHOOK_NAME, MERGE_REQUEST_WEBHOOK_DESCRIPTION, MERGE_REQUEST_WEBHOOK_SETTINGS),
    ):
        existing = next((hook for hook in hooks if hook.get('url') == url), None)
        data = dict(COMMON_WEBHOOK_SETTINGS)
        data.update(settings)
        data.update({'url': url, 'name': name, 'description': description, 'token': secret_token})

        if dry_run:
            print(f'[INFO]: (dry-run) {micro_project}: se va a {"actualizar" if existing else "crear"} el webhook "{name}" → {url}')
            results.append(f'"{name}" se va a {"actualizar" if existing else "crear"}')
            continue
        if existing:
            gitlab('PUT', f'{hooks_api}/{existing["id"]}', data)
        else:
            gitlab('POST', hooks_api, data)
        state = 'actualizado' if existing else 'creado'
        print(f'[INFO]: ✅ {micro_project}: webhook "{name}" {state}')
        results.append(f'"{name}" {state}')
    return ', '.join(results)


# --- Paso 3: pipelines -----------------------------------------------------------------------------------------------

def jenkins_exists(path_parts):
    return jenkins('GET', jenkins_job_url(path_parts) + '/api/json?tree=name') == 200


def jenkins_create(parent_parts, name, xml):
    status = jenkins('POST', jenkins_job_url(parent_parts) + '/createItem?name=' + urllib.parse.quote(name), xml)
    if status not in (200, 201, 302):
        raise RuntimeError(f'Jenkins respondió HTTP {status} al crear {"/".join(parent_parts + [name])}')


def create_pipelines(micro, dry_run):
    print('[INFO]: ---------- 3. Pipelines en Jenkins ----------')
    micro_parts = [PAIS, APPCODE, micro]
    repo_url = CONFIG_REPO_URL.strip() or f'{GITLAB_URL.strip().rstrip("/")}/{clean_project(CONFIG_PROJECT)}.git'
    branch = CONFIG_REPO_BRANCH.strip() or f'*/{TARGET_BRANCH}'
    connection = GITLAB_CONNECTION.strip()
    created, existing = 0, 0

    for i in range(len(micro_parts)):
        current = micro_parts[:i + 1]
        full = '/'.join(current)
        if jenkins_exists(current):
            print(f'[INFO]: Carpeta {full}: ya existía')
            continue
        if not dry_run:
            jenkins_create(current[:-1], current[-1], FOLDER_XML)
        print(f'[INFO]: ✅ Carpeta {full}: {"se va a crear (dry-run)" if dry_run else "creada"}')
        if dry_run:
            # En dry-run la carpeta no se crea: lo que va dentro tampoco existe
            for deeper in range(i + 1, len(micro_parts)):
                print(f'[INFO]: ✅ Carpeta {"/".join(micro_parts[:deeper + 1])}: se va a crear (dry-run)')
            break

    for job_name, config_name in PIPELINES.items():
        job_parts = micro_parts + [job_name]
        full = '/'.join(job_parts)
        if jenkins_exists(job_parts):
            print(f'[INFO]: Job {full}: ya existía (no se modificó)')
            existing += 1
            continue
        config_path = CONFIG_PATH_TEMPLATE.format(pais=PAIS, appcode=APPCODE, micro=micro, config=config_name)
        xml = JOB_XML.format(
            gitlab_connection_property=GITLAB_CONNECTION_XML.format(connection=escape(connection)) if connection else '',
            description=escape(f'Pipeline {job_name} de {micro} (creado por create_micro_setup.py)'),
            repo_url=escape(repo_url),
            credentials_id=escape(CONFIG_REPO_CREDENTIALS_ID),
            branch=escape(branch),
            config_path=escape(config_path),
            template_path=escape(PIPELINE_TEMPLATE_PATH),
        )
        if not dry_run:
            jenkins_create(micro_parts, job_name, xml)
        print(f'[INFO]: ✅ Job {full}: {"se va a crear (dry-run)" if dry_run else "creado"} → config {config_path}')
        created += 1

    return f'jobs {"a crear" if dry_run else "creados"} {created}, ya existían {existing}'


# --- main ------------------------------------------------------------------------------------------------------------

def read_micro():
    args = [arg for arg in sys.argv[1:] if not arg.startswith('--')]
    micro = (args[0] if args else MICRO or input('Nombre del micro (= nombre del repo en GitLab): ')).strip().strip('/')
    if not micro or '/' in micro or ' ' in micro:
        sys.exit(f'[ERROR]: ❌ Nombre de micro no válido: \'{micro}\' (solo el nombre, ej. micro1).')
    return micro


def main():
    global OPENER, GITLAB_TOKEN, JENKINS_AUTH
    dry_run = '--dry-run' in sys.argv[1:]
    micro = read_micro()

    for name, value in (('PAIS', PAIS), ('APPCODE', APPCODE)):
        if not value.strip() or '/' in value.strip('/'):
            sys.exit(f'[ERROR]: ❌ {name} no válido: \'{value}\' (solo el nombre).')
    if not PIPELINES:
        sys.exit('[ERROR]: ❌ PIPELINES está vacío.')
    if not os.path.isfile(TEMPLATE_FILE):
        sys.exit(f'[ERROR]: ❌ No existe la plantilla {TEMPLATE_FILE}')

    GITLAB_TOKEN = (GITLAB_API_TOKEN or os.environ.get('GITLAB_API_TOKEN') or getpass.getpass('Token de GitLab (scope api): ')).strip()
    secret_token = (WEBHOOK_SECRET_TOKEN or os.environ.get('WEBHOOK_SECRET_TOKEN') or getpass.getpass('Secret token de los webhooks: ')).strip()
    jenkins_user = (JENKINS_USER or os.environ.get('JENKINS_USER') or input('Usuario de Jenkins: ')).strip()
    jenkins_token = (JENKINS_API_TOKEN or os.environ.get('JENKINS_API_TOKEN') or getpass.getpass('API token de Jenkins: ')).strip()
    if not GITLAB_TOKEN or not secret_token or not jenkins_user or not jenkins_token:
        sys.exit('[ERROR]: ❌ Falta el token de GitLab, el secret token o el usuario/API token de Jenkins.')
    JENKINS_AUTH = 'Basic ' + b64encode(f'{jenkins_user}:{jenkins_token}'.encode('utf-8')).decode('ascii')
    OPENER = build_opener()

    micro_project = clean_project(MICRO_PROJECT_TEMPLATE.format(pais=PAIS, appcode=APPCODE, micro=micro))
    config_project = clean_project(CONFIG_PROJECT)
    print(f'[INFO]: Micro: {micro} | GitLab: {micro_project} | Jenkins: {PAIS}/{APPCODE}/{micro}'
          f'{" | DRY-RUN (no se cambia nada)" if dry_run else ""}')

    # Validaciones antes de cambiar nada
    try:
        if gitlab('GET', gitlab_project_api(micro_project)) is None:
            sys.exit(f'[ERROR]: ❌ No existe el repo del micro en GitLab: {micro_project} (revisa GITLAB_URL, PAIS, APPCODE y el nombre).')
        if gitlab('GET', gitlab_project_api(config_project)) is None:
            sys.exit(f'[ERROR]: ❌ No existe el repo de configuraciones en GitLab: {config_project} (revisa CONFIG_PROJECT).')
        if jenkins('GET', JENKINS_URL.strip().rstrip('/') + '/api/json?tree=mode') != 200:
            sys.exit(f'[ERROR]: ❌ Jenkins no responde en {JENKINS_URL} (revisa JENKINS_URL).')
    except RuntimeError as error:
        print(f'[ERROR]: ❌ {error}')
        sys.exit('[INFO]: 401 = token/usuario inválido; 403 = sin permiso.')
    print('[INFO]: ✅ Repo del micro, repo de configuraciones y Jenkins accesibles')

    summary = {}
    steps = (
        ('1. Config', lambda: create_config(micro, dry_run)),
        ('2. Webhooks', lambda: create_webhooks(micro_project, secret_token, dry_run)),
        ('3. Pipelines', lambda: create_pipelines(micro, dry_run)),
    )
    for step_name, step in steps:
        try:
            summary[step_name] = step()
        except (RuntimeError, OSError) as error:
            print(f'[ERROR]: ❌ {step_name}: {error}')
            summary[step_name] = '❌ error'
            for pending_name, _ in steps[len(summary):]:
                summary[pending_name] = 'no se ejecutó'
            break

    print('[INFO]: ---------- Resumen ----------')
    for step_name, result in summary.items():
        print(f'[INFO]: {step_name}: {result}')
    if any('error' in result for result in summary.values()):
        print('[INFO]: 401 = token/usuario inválido; 403 = sin permiso o rama protegida; 400 = nombre ya existe o XML no válido.')
        sys.exit(1)
    print(f'[INFO]: ✅ Micro {micro} {"revisado (dry-run)" if dry_run else "dado de alta"}')


if __name__ == '__main__':
    main()
