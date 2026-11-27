# jte — CI/CD de componentes JBoss con Jenkins Templating Engine

Pipelines de Jenkins (JTE) para los micros de GitLab: orquestador de webhooks, validación de Merge Requests, CI (Maven, Sonar)
y despliegue en JBoss por SSH.

| Carpeta | Contenido |
|---|---|
| `jte-pipeline-configurations-automations/` | Configuración de cada job (solo valores) |
| `jte-automations-pipeline-libraries/` | Librerías con la lógica (steps) |
| `jte-pipelines-template/` | Templates que orquestan las etapas |

Este repositorio es **público**: aquí no van IPs, usuarios ni secretos reales. Esos valores viven en Jenkins (propiedades de carpeta y
credenciales), como se describe abajo.

---

## Configuración de Jenkins

### 1. Propiedades de la carpeta del país

En la carpeta del país (ej. `mexico`) → **Configure → Folder Properties**. Todos los jobs que estén **dentro** de esa carpeta (micros,
jobs de despliegue, orquestador y validador de MR) las heredan. Una subcarpeta puede redefinir una propiedad como excepción.

| Propiedad | Valor de ejemplo | La usan |
|---|---|---|
| `GITLAB_URL` | `https://gitlab.empresa.com` (sin `/` final) | `checkout_code` (clonar) y `validate_merge_request` (cerrar MR) |
| `GITLAB_CREDENTIALS_ID` | `gitlab-checkout` | `checkout_code`: ID de la credencial para clonar |
| `F_PIPELINE_JDK_TOOL_VERSION` | nombre del JDK en *Manage Jenkins → Tools* | steps de `maven` y `scan_code_quality` |
| `F_PIPELINE_MAVEN_TOOL_VERSION` | nombre de Maven en *Manage Jenkins → Tools* | steps de `maven` y `scan_code_quality` |
| `JBOSS_SSH_USER` | usuario SSH de los servidores JBoss | `jboss_ci` y `jboss_cd_*` |
| `JBOSS_TEMP_PATH` | `/opt` (ruta absoluta, sin `/` final) | `jboss_ci` (carpeta temporal) y `jboss_cd_*` (de dónde despliega) |

Lista para copiar:

```
GITLAB_URL
GITLAB_CREDENTIALS_ID
F_PIPELINE_JDK_TOOL_VERSION
F_PIPELINE_MAVEN_TOOL_VERSION
JBOSS_SSH_USER
JBOSS_TEMP_PATH
```

### 2. Credenciales

En **Manage Jenkins → Credentials**.

| ID | Tipo (*Kind*) | Contenido | Para qué |
|---|---|---|---|
| el valor de `GITLAB_CREDENTIALS_ID` (ej. `gitlab-checkout`) | Username with password | usuario de GitLab + token con `read_repository` | Clonar los repos |
| `gitlab-webhook-token` | Username with password | usuario cualquiera + **password = Secret token** de los webhooks | Webhooks del orquestador y del validador de MR |
| `gitlab-api-close-merge-request` | Secret text | token de GitLab con scope **`api`** (usuario con rol Maintainer) | Comentar y cerrar MR inválidos |
| `jboss-cli` | Username with password | usuario y clave de administración de JBoss | `jboss-cli` (info, undeploy, deploy) |
| la de la conexión de GitLab (ver punto 3) | GitLab API token | token de GitLab con scope **`api`** | Estado por etapa en GitLab |

Lista para copiar:

```
gitlab-checkout
gitlab-webhook-token
gitlab-api-close-merge-request
jboss-cli
```

> El token de `gitlab-api-close-merge-request` puede ser el mismo de la conexión de GitLab, pero guardado como **Secret text**:
> el pipeline no puede leer las credenciales de tipo *GitLab API token*.

### 3. Configuración global (Manage Jenkins)

Nombres que el código espera encontrar **exactamente así**:

| Dónde | Nombre / ID | Para qué |
|---|---|---|
| *System → GitLab* (Connection name) | `api-gitlab_connection_java_repositorie` | Estado por etapa en GitLab (`gitlab_stage`) |
| *System → SonarQube servers* (Name) | `Sonar_server_SCAN_SERVER` | Análisis de Sonar (`scan_code_quality`) |
| *Managed files* (Config File Provider) | `GLOBAL_MAVEN_SETTINGS` | `settings.xml` de Maven |
| *Tools → JDK* y *Tools → Maven* | los nombres que pongas en `F_PIPELINE_JDK_TOOL_VERSION` / `F_PIPELINE_MAVEN_TOOL_VERSION` | `withMaven` |

---

## Configuración de GitLab

| Dónde | Qué |
|---|---|
| Grupo del país → *Settings → Webhooks* | Webhook del **orquestador**: URL del job (`https://<jenkins>/project/<ruta-del-job>`), Secret token, solo **Push events** |
| Grupo del país → *Settings → Webhooks* | Webhook del **validador de MR**: URL del job, mismo Secret token, solo **Merge request events** |
| Cada repo → *Settings → Repository → Protected branches* | `dev`, `qa`, `uat`, `main`: *Allowed to merge* = Maintainers, *Allowed to push and merge* = **No one** |
| SonarQube → *Administration → Configuration → Webhooks* | Webhook global a `https://<jenkins>/sonarqube-webhook/` (Quality Gate) |

> La URL del webhook incluye la carpeta y el nombre del job: si se renombra o se mueve un job en Jenkins, hay que actualizar la URL
> en el webhook de GitLab.

### Crear los webhooks con script

En `webhooks/` hay dos scripts de Python 3 (solo librería estándar) que crean o actualizan los webhooks **por repo**, con nombre
descriptivo, Secret token y *Enable SSL verification* desactivado:

| Script | Webhook | Eventos |
|---|---|---|
| `webhooks/create_push_webhook.py` | Orquestador (`push_orchestrator`) | Push events |
| `webhooks/create_merge_request_webhook.py` | Validador de MR (`merge_request_validator`) | Merge request events |

1. En cada script, editar `JENKINS_WEBHOOK_URL` (URL del job) y `REPOSITORIES` (URL de cada repo, ej. `https://gitlab.com.co/pais/codigo/repo1`).
   Si GitLab NO está en la raíz del dominio (ej. `https://servidor/gitlab/...`), poner esa base en `GITLAB_URL`: sin ella la API se
   llama en la raíz del dominio y puede responder otro servidor (ej. Jenkins con "Authentication required").
2. Ejecutar `python3 webhooks/create_push_webhook.py` (o con `--dry-run` para ver qué haría sin cambiar nada).
3. El script pide el **token de GitLab** (scope `api`, rol Maintainer en los repos) y el **Secret token** (el password de
   `gitlab-webhook-token`). No se guardan en el repo; también se pueden pasar con las variables `GITLAB_API_TOKEN` y `WEBHOOK_SECRET_TOKEN`.

Si el repo ya tiene un webhook con la misma URL, lo actualiza en vez de duplicarlo.

**Proxy:** poner `PROXY_URL` en cada script (ej. `http://proxy.empresa.com:8080`, sin usuario ni clave) o la variable `GITLAB_PROXY_URL`;
vacío = se usa `HTTPS_PROXY` del entorno si existe. Si el proxy pide usuario: variable `PROXY_USER` (la clave se pide al ejecutar o va en `PROXY_PASSWORD`).

### Crear los pipelines de los micros con script

`webhooks/create_micro_pipelines.py` (Python 3, solo librería estándar) crea en Jenkins `<pais>/<appcode>/<micro>/{integration, dev, qa, uat}` con la
definición JTE (repo de configuraciones + `pipelineConfigurationPath` de cada ambiente). Las carpetas y los jobs que **ya existen no se
tocan** (no se sobrescriben). Los archivos de config de cada micro se crean a mano en el repo de configuraciones.

1. Editar la configuración del script: `JENKINS_URL` (base, sin `/` final), `CONFIG_REPO_URL` / `CONFIG_REPO_CREDENTIALS_ID` /
   `CONFIG_REPO_BRANCH` (los del job que ya funciona), `CONFIG_PATH_TEMPLATE` (`{pais}`, `{appcode}`, `{micro}`, `{config}`), `PIPELINES` y `MICROS`.
2. Ejecutar `python3 webhooks/create_micro_pipelines.py --dry-run` y luego sin `--dry-run`.
3. Usuario y **API token de Jenkins** en `JENKINS_USER` / `JENKINS_API_TOKEN` (o variables de entorno, o se piden al ejecutar). Proxy:
   `PROXY_URL` o `JENKINS_PROXY_URL`.
