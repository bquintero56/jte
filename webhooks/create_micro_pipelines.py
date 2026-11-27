#!/usr/bin/env python3
"""
Crea en Jenkins los pipelines JTE de cada micro: <pais>/<appcode>/<micro>/{dev, qa, uat}.

Cada job queda con la definición "Jenkins Templating Engine" (AdHocTemplateFlowDefinition) apuntando al repo de configuraciones
y a SU archivo de config (dev → dev-integration-config.groovy, qa → qa-..., uat → uat-...). Los archivos de config de cada micro se
crean a mano en el repo; este script solo crea los jobs que apuntan a ellos.
  - Carpetas (<pais>, <appcode>, <micro>): se crean solo si no existen.
  - Jobs: si YA EXISTEN no se tocan (no se sobrescriben); solo se crean los que faltan.
  - La GitLab connection (GITLAB_CONNECTION) va en el job desde que se crea. Las demás propiedades (log rotator, variables...)
    las pone el template en la primera ejecución (properties([...])).

Uso (solo librería estándar de Python 3, sin pip install):
  1. Editar la CONFIGURACIÓN de abajo (URL de Jenkins, repo de configuraciones, ruta de las configs y MICROS).
  2. Ejecutar:  python3 create_micro_pipelines.py            (o --dry-run: solo muestra lo que haría, sin cambiar nada)
     Usuario y API token de Jenkins: campos JENKINS_USER / JENKINS_API_TOKEN de abajo, variables de entorno con el mismo nombre,
     o se piden al ejecutar. API token: Jenkins → tu usuario → Configure → API Token.
     Proxy: PROXY_URL abajo (o variable de entorno JENKINS_PROXY_URL); si pide usuario, variable de entorno PROXY_USER.

Repo público: NO hacer commit de este archivo con tokens, usuarios ni URLs internas reales.
"""

import getpass
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

# URL de Jenkins (sin / final)
JENKINS_URL = 'https://jenkins.ejemplo.com'

# Usuario y API token de Jenkins. Vacíos = variables de entorno JENKINS_USER / JENKINS_API_TOKEN o se piden al ejecutar.
# ⚠️ Si los escribes aquí, NO hagas commit de este archivo con los valores: el repo es público.
JENKINS_USER = ''
JENKINS_API_TOKEN = ''

# Proxy para llegar a Jenkins (ej. 'http://proxy.empresa.com:8080'), SIN usuario ni clave. Vacío = HTTPS_PROXY del entorno o sin proxy.
PROXY_URL = ''

# Repo Git con las configuraciones JTE (el mismo que usa tu job que ya funciona)
CONFIG_REPO_URL = 'https://gitlab.ejemplo.com/grupo/pipelines.git'
CONFIG_REPO_CREDENTIALS_ID = 'GITLAB_CLONE'      # ID de la credencial de Jenkins para clonar ese repo
CONFIG_REPO_BRANCH = '*/main'

# Ruta del archivo de config dentro del repo. Variables: {pais}, {appcode}, {micro}, {env}
CONFIG_PATH_TEMPLATE = 'jte-jboss-configurations/jboss-micro/{appcode}/{micro}/pipeline_configurations/{env}-integration-config.groovy'

# GitLab connection del GitLab Plugin (Manage Jenkins → System → GitLab → Connection name). Es distinta de la credencial de clonado.
# Vacío = el job se crea sin GitLab connection.
GITLAB_CONNECTION = 'gitlab'

# Campo "Pipeline Template Path" del job (igual que en tu job actual; el template real lo elige la config con pipeline_template)
PIPELINE_TEMPLATE_PATH = 'Jenkinsfile'

# Pipelines que se crean dentro de cada micro (nombre del job = {env})
ENVIRONMENTS = ['dev', 'qa', 'uat']

# Micros: ruta en Jenkins <pais>/<appcode>/<micro> (la misma que en GitLab)
MICROS = [
    'pais/appcode/micro1',
    # 'pais/appcode/micro2',
]

# ---------------------------------------------------------------------------------------------------------------------

FOLDER_XML = """<?xml version='1.1' encoding='UTF-8'?>
<com.cloudbees.hudson.plugins.folder.Folder>
  <description></description>
  <properties/>
</com.cloudbees.hudson.plugins.folder.Folder>
"""

# Plantilla del job: definición JTE copiada del config.xml de un job que ya funciona (solo cambia pipelineConfigurationPath)
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
AUTH_HEADER = ''


def build_opener():
    """Cliente HTTP: con PROXY_URL (y usuario/clave si PROXY_USER está definido) o, si está vacío, con el proxy del entorno."""
    proxy_url = (os.environ.get('JENKINS_PROXY_URL') or PROXY_URL).strip()
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


def job_url(path_parts):
    """['pais', 'appcode', 'micro1'] → https://jenkins/job/pais/job/appcode/job/micro1"""
    return JENKINS_URL.rstrip('/') + ''.join(f'/job/{urllib.parse.quote(part)}' for part in path_parts)


def jenkins_request(method, url, data=None):
    """Llama a Jenkins y devuelve el código HTTP. Lanza RuntimeError si no conecta o responde un error (salvo 404)."""
    headers = {'Authorization': AUTH_HEADER}
    body = None
    if data is not None:
        body = data.encode('utf-8')
        headers['Content-Type'] = 'application/xml; charset=utf-8'
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with OPENER.open(request, timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return 404
        message = error.read().decode('utf-8', errors='replace')[:500]
        raise RuntimeError(f'HTTP {error.code} en {method} {url}: {message}') from None
    except urllib.error.URLError as error:
        raise RuntimeError(f'No se pudo conectar a {url}: {error.reason}') from None


def exists(path_parts):
    return jenkins_request('GET', job_url(path_parts) + '/api/json?tree=name') == 200


def create_item(parent_parts, name, xml, dry_run):
    """Crea <name> dentro de la carpeta parent_parts (o en la raíz) con el config.xml dado."""
    url = job_url(parent_parts) + '/createItem?name=' + urllib.parse.quote(name)
    if dry_run:
        return
    status = jenkins_request('POST', url, xml)
    if status not in (200, 201, 302):
        raise RuntimeError(f'Jenkins respondió HTTP {status} al crear {"/".join(parent_parts + [name])}')


def ensure_folders(path_parts, dry_run, summary):
    """Crea las carpetas de la ruta que no existan (pais → appcode → micro)."""
    for i in range(len(path_parts)):
        current = path_parts[:i + 1]
        full = '/'.join(current)
        if full in summary['folders_seen']:
            continue
        summary['folders_seen'].add(full)
        if exists(current):
            print(f'[INFO]: Carpeta {full}: ya existía')
            continue
        create_item(current[:-1], current[-1], FOLDER_XML, dry_run)
        print(f'[INFO]: ✅ Carpeta {full}: {"se va a crear (dry-run)" if dry_run else "creada"}')
        summary['folders_created'] += 1


def ensure_job(micro_parts, env, dry_run, summary):
    pais, appcode, micro = micro_parts[-3], micro_parts[-2], micro_parts[-1]
    job_parts = micro_parts + [env]
    full = '/'.join(job_parts)

    if exists(job_parts):
        print(f'[INFO]: Job {full}: ya existía (no se modificó)')
        summary['jobs_existing'] += 1
        return

    config_path = CONFIG_PATH_TEMPLATE.format(pais=pais, appcode=appcode, micro=micro, env=env)
    connection = GITLAB_CONNECTION.strip()
    xml = JOB_XML.format(
        gitlab_connection_property=GITLAB_CONNECTION_XML.format(connection=escape(connection)) if connection else '',
        description=escape(f'Pipeline {env} de {micro} (creado por create_micro_pipelines.py)'),
        repo_url=escape(CONFIG_REPO_URL),
        credentials_id=escape(CONFIG_REPO_CREDENTIALS_ID),
        branch=escape(CONFIG_REPO_BRANCH),
        config_path=escape(config_path),
        template_path=escape(PIPELINE_TEMPLATE_PATH),
    )
    create_item(micro_parts, env, xml, dry_run)
    print(f'[INFO]: ✅ Job {full}: {"se va a crear (dry-run)" if dry_run else "creado"} → config {config_path}')
    summary['jobs_created'] += 1


def main():
    global OPENER, AUTH_HEADER
    dry_run = '--dry-run' in sys.argv[1:]

    if not MICROS:
        sys.exit('[ERROR]: ❌ MICROS está vacío: agrega las rutas <pais>/<appcode>/<micro>.')
    if not ENVIRONMENTS:
        sys.exit('[ERROR]: ❌ ENVIRONMENTS está vacío.')

    user = (JENKINS_USER or os.environ.get('JENKINS_USER') or input('Usuario de Jenkins: ')).strip()
    token = (JENKINS_API_TOKEN or os.environ.get('JENKINS_API_TOKEN') or getpass.getpass('API token de Jenkins: ')).strip()
    if not user or not token:
        sys.exit('[ERROR]: ❌ Falta el usuario o el API token de Jenkins.')

    AUTH_HEADER = 'Basic ' + b64encode(f'{user}:{token}'.encode('utf-8')).decode('ascii')
    OPENER = build_opener()

    print(f'[INFO]: Jenkins: {JENKINS_URL} | micros: {len(MICROS)} | pipelines por micro: {", ".join(ENVIRONMENTS)}'
          f'{" | DRY-RUN (no se cambia nada)" if dry_run else ""}')

    summary = {'folders_seen': set(), 'folders_created': 0, 'jobs_created': 0, 'jobs_existing': 0}
    failed = []
    for micro_path in MICROS:
        micro_parts = [part for part in micro_path.strip().strip('/').split('/') if part]
        if len(micro_parts) < 3:
            print(f'[ERROR]: ❌ {micro_path}: la ruta debe ser <pais>/<appcode>/<micro>')
            failed.append(micro_path)
            continue
        try:
            ensure_folders(micro_parts, dry_run, summary)
            for env in ENVIRONMENTS:
                ensure_job(micro_parts, env, dry_run, summary)
        except RuntimeError as error:
            print(f'[ERROR]: ❌ {micro_path}: {error}')
            failed.append(micro_path)

    print(f'[INFO]: Resumen: carpetas creadas {summary["folders_created"]}, jobs creados {summary["jobs_created"]}, '
          f'jobs que ya existían {summary["jobs_existing"]}, micros con error {len(failed)}')
    if failed:
        print('[INFO]: 401 = usuario o API token inválido; 403 = sin permiso para crear jobs; 400 = el nombre ya existe o el XML no es válido.')
        sys.exit(1)


if __name__ == '__main__':
    main()
