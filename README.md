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
