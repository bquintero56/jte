# Contexto del proyecto: CI/CD de componentes JBoss con Jenkins + JTE

Idioma de trabajo: **español** (comentarios, logs, mensajes de error y correos).

> Estado: **estructura inicial**. Las secciones marcadas con `POR COMPLETAR` las llena el usuario (arquitectura, servidores,
> componentes, flujo actual). Claude no debe inventar esos datos: si faltan, preguntar.

## 1. Qué es esto

Pipelines de **despliegue (CI/CD)** que corren en Jenkins con **Jenkins Templating Engine (JTE)** y despliegan componentes en
servidores **JBoss / WildFly** por **SSH** desde el nodo de Jenkins.

- `POR COMPLETAR`: qué componentes se despliegan (`.war`, `.ear`, `.jar`, módulos, configuración), en qué servidores y ambientes.
- `POR COMPLETAR`: cómo se hace hoy (manual, scripts existentes, `jboss-cli`, copia a `deployments/`...). "Ya tengo algo": pegarlo aquí.

**Repos hermanos (referencia, mismo estándar JTE, ya funcionando en Jenkins):**
- `jte-automatizaciones` (`C:\Users\bquin\Desktop\jte\jte-automatizaciones\jte-automatizaciones`): pipelines por **SSH + sudo** desde el nodo,
  correo HTML con `emailext`, adjuntos, CC, cron con zona horaria. Su CLAUDE.md documenta el patrón SSH probado.
- `jte-automatizaciones-postman` (`C:\Users\bquin\Desktop\jte\jte-automatizaciones-postman`): `curl` con OAuth 2.0, proxy con credencial,
  certificados, credenciales con `withCredentials`, diagnóstico de errores en log.

Reutilizar de ahí lo que sirva en lugar de reinventarlo.

## 2. Cómo funciona JTE (resumen para trabajar aquí)

JTE separa un pipeline en tres piezas. Un **template** común orquesta; cada job aporta solo su **configuración**; la **lógica** vive en
**librerías** reutilizables.

| Pieza | Archivo | Qué hace |
|---|---|---|
| **Pipeline configuration** | `pipeline_config.groovy` | Solo valores: qué template usar, qué librerías cargar y sus parámetros. DSL propio (no es Groovy de pipeline). |
| **Pipeline template** | `<nombre>.groovy` | El "Jenkinsfile" genérico: llama a los steps en orden (`stage`, `node`, `try/finally`). Sin valores quemados. |
| **Librería** | `<libreria>/library_config.groovy` + `<libreria>/steps/*.groovy` | Cada archivo de `steps/` es un step que el template invoca por su nombre. `library_config.groovy` valida los campos de la config. |

### Bloques del `pipeline_config.groovy`

```groovy
jte {
    allow_scm_jenkinsfile = false                       // ignora un Jenkinsfile en el repo de la app
    pipeline_template     = 'desplegar-jboss/desplegar_jboss.groovy'
}

keywords {                                              // variables globales que el template lee directamente
    jenkins_agent_label = 'linux-pro'
    execution { time = 30; units = 'MINUTES' }
}

application_environments {                              // ambientes: cada uno queda como variable en el template (dev, qa, prod)
    dev  { long_name = 'Desarrollo' }
    qa   { long_name = 'QA' }
    prod { long_name = 'Producción' }
}

libraries {
    despliegue_jboss {                                  // = nombre de la carpeta de la librería
        campo = 'valor'                                 // llega al step como config.campo
    }
    notificacion_correo { destinatarios = '...' }
}
```

- **`application_environments`** es la pieza clave para CI/CD: cada ambiente es un objeto que el template puede pasar a un step
  (`desplegar(qa)`), y puede tener campos propios (`servidores`, `rutaDeployments`...). Evita duplicar config por ambiente.
- **`keywords`**: valores globales accesibles en el template por su nombre (`jenkins_agent_label`, `execution.time`).

### Reglas que JTE exige (si se rompen, el job falla)

- Nombre del bloque en `libraries { }` = **nombre exacto de la carpeta** de la librería.
- Nombre del archivo del step = nombre con que se llama (`desplegar_componente.groovy` → `desplegar_componente(...)`).
- Dentro de un step, `config` es el bloque de **esa** librería.
- **Todo campo** de la config debe estar en `library_config.groovy` (`required { }` u `optional { }`), si no: `field 'x' is not used`.
  Tipos: `String`, `Integer`, `Boolean`, `List`, `Map`, etc.
- Un step es `def call(...)` (puede recibir argumentos y devolver valores) y puede usar cualquier step de Jenkins (`sh`, `withCredentials`...).
- **Hooks** opcionales en steps con anotaciones: `@Init`, `@Validate`, `@CleanUp`, `@Notify`, `@BeforeStep`, `@AfterStep`
  (ej. un `@CleanUp` que limpie el workspace o un `@Notify` que mande el correo siempre).
- **Recursos de librería**: una carpeta `resources/` dentro de la librería; desde un step se lee con `resource('script.sh')`. Útil para
  scripts shell o `jboss-cli` largos sin meterlos en strings Groovy.

### Cómo se configura en Jenkins

- El job (o la carpeta que lo contiene) debe usar **JTE**: *Pipeline → Definition: Jenkins Templating Engine*, o fuentes JTE en la carpeta
  (*Folder → Configure → Jenkins Templating Engine*), con este repo y su *Base Directory*:

  | Fuente JTE | Base Directory |
  |---|---|
  | Pipeline Configuration / Templates | `jte-pipeline-configurations-automations/<job>` y `jte-pipelines-template` |
  | Library Source | `jte-automations-pipeline-libraries` |

- **Error ya visto:** `No such DSL method 'libraries'` → el job ejecuta `pipeline_config.groovy` como Jenkinsfile normal (*Pipeline script
  from SCM*). Hay que cambiar el job a JTE.
- `No such DSL method '<step>'` → falta la librería en `libraries { }` o el nombre del archivo del step no coincide.
- *In-process Script Approval*: si Jenkins pide aprobar métodos, aprobarlos y relanzar.

## 3. Arquitectura del repo (1 repo, 3 carpetas)

| Carpeta | Qué contiene | Quién lo usa |
|---|---|---|
| `jte-pipeline-configurations-automations` | Una carpeta por job con su `pipeline_config.groovy`: **solo valores**. | Todos los steps vía `config` |
| `jte-automations-pipeline-libraries` | Una carpeta por librería con `library_config.groovy` y `steps/*.groovy` (la **lógica**). | El template |
| `jte-pipelines-template` | Una carpeta por tipo de pipeline con el template `.groovy` que solo **orquesta**. | Jenkins |

Estructura propuesta (nombres a confirmar con la arquitectura real):

```
jte-pipeline-configurations-automations/
└── desplegar-<componente>/pipeline_config.groovy     servidores, rutas, ambientes, artefacto, destinatarios

jte-automations-pipeline-libraries/
├── despliegue_jboss/
│   ├── library_config.groovy
│   ├── resources/                                    scripts shell / jboss-cli (opcional)
│   └── steps/                                        POR COMPLETAR: obtener artefacto, respaldar, copiar, desplegar, verificar, rollback
└── notificacion_correo/
    ├── library_config.groovy
    └── steps/enviar_correo_despliegue.groovy

jte-pipelines-template/
└── desplegar-jboss/desplegar_jboss.groovy
```

Estándar de nombres (igual que los repos hermanos): carpetas de config y template con guiones (`desplegar-jboss`), librerías y
steps con guion bajo (`despliegue_jboss`, `desplegar_componente`), verbos en infinitivo.

## 4. Convenciones técnicas (heredadas de los repos hermanos)

1. **Config = valores, librería = lógica, template = orquestación.** Nada quemado en los steps (IPs, rutas, usuarios, versiones salen de
   `config`). Un campo nuevo va en la config **y** en `library_config.groovy`.
2. **Nunca secretos en el repo** (si es público o se comparte). Usuarios/claves/llaves en **credenciales de Jenkins** (`withCredentials`,
   `sshagent`) y en la config solo el `credentialsId`. Los secretos se usan como variables de shell (`\$VAR`), nunca interpolados en Groovy.
3. **Log limpio.** Los `sh` usan `"""#!/bin/sh` pegado a las comillas (sin shebang Jenkins corre `sh -x` y llena el log de trazas).
   Continuaciones de línea dentro del bloque: `\\`. En GString, `$(...)` y `$VAR` de shell se escriben `\$(...)` y `\$VAR`.
4. **Logs con `println` y prefijo** (preferencia del usuario, no `echo`): `println("[INFO]: ...")`, `println("[WARN]: ⚠️ ...")`,
   `error("[ERROR]: ❌ ...")`, `unstable("[WARN]: ⚠️ ...")`; ✅ en los éxitos (`[INFO]: ✅ ...`) por paso y servidor, y un **resumen final**.
5. **Estructura del template:** `timeout(execution)` → `node(jenkins_agent_label)` → `withFolderProperties` → `try/catch/finally`, con
   `clean_job_workspace()` en el `finally` (viene de una librería global de la instalación). Cada tarea es un `stage`. Todo
   `catch (Exception e)` va precedido de `catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) { throw e }`.
6. **CPS de Jenkins:** `each` con closures que llaman steps funciona en la instalación del usuario y es su preferencia (ej.
   `host_list.each { target_host -> ... }`); evitar `collect`/`count {}` con steps adentro (`findAll {}.size()` en vez de `count {}`).
7. **Fechas con `date` en el nodo** con la zona horaria de la config (`TZ='<zona>' date +'<formato>'`).
8. **Correo** con `emailext`, `mimeType: 'text/html'`, `to` = destinatarios + `cc:` por cada copia. Se envía **siempre** (éxito o fallo).
9. **Diferencia con los repos de monitoreo:** en un despliegue un fallo **sí debe detener** los pasos siguientes (no desplegar en el
   servidor 2 si falló el 1, salvo que se decida lo contrario) y marcar el build en `FAILURE`; aun así el correo se envía desde el `finally`
   o un hook `@Notify`.

## 5. Despliegue JBoss por SSH — `POR COMPLETAR` / decidir

Patrón SSH ya probado en `jte-automatizaciones` (el nodo tiene la llave, sin `credentialsId`):
`ssh -o StrictHostKeyChecking=no <user>@<host> "sudo -n -u <runAsUser> <comando>"` (`sudo -n` para que no se cuelgue pidiendo clave).
Alternativa si la llave está en Jenkins: `sshagent(['<credentialsId>']) { sh 'ssh ...' }`.

Preguntas a responder con la arquitectura del usuario:

1. **Origen del artefacto:** ¿se compila en Jenkins (Maven/Gradle), se descarga de Nexus/Artifactory, o se toma de una ruta? ¿Versión por
   parámetro del job?
2. **Modo JBoss:** *standalone* o *domain*; versión de JBoss EAP / WildFly.
3. **Método de despliegue:**
   - `jboss-cli.sh --connect --command="deploy /tmp/app.war --force"` (recomendado: reporta error de despliegue), o
   - copiar a `standalone/deployments/` y vigilar los marcadores `.deployed` / `.failed` / `.isdeploying`, o
   - el script que ya existe.
4. **Servidores y ambientes:** lista de hosts por ambiente (dev/qa/prod), usuario SSH, usuario de JBoss (`sudo -u`), rutas.
5. **Orden y alcance:** ¿todos los servidores en secuencia, uno por uno sacándolo del balanceador, o en paralelo?
6. **Respaldo y rollback:** respaldar el artefacto actual antes de desplegar; ¿rollback automático si falla la verificación?
7. **Reinicio:** ¿hace falta reiniciar JBoss o basta el redeploy en caliente?
8. **Verificación post-despliegue:** `curl` a un health check, revisar `server.log`, estado del deployment en `jboss-cli`.
9. **Aprobaciones:** ¿`input` manual antes de prod? ¿quién aprueba?
10. **Disparador:** manual con parámetros (componente, versión, ambiente), webhook de Git, o cadena desde un job de build.
11. **Repo Git:** `https://github.com/bquintero56/jte` (**público**: nada de secretos, IPs ni hosts internos reales en el repo), rama `main`.

## 5.1 Orquestador de webhooks de GitLab (`webhook`)

Migrado del orquestador que el usuario tenía con **Bitbucket**; se mantiene su lógica lo más posible. Un **solo job** recibe el
webhook (push) de **todos** los proyectos de GitLab con el **GitLab Plugin** (trigger "Build when a change is pushed to GitLab",
declarado en el template con `properties`, Secret token = password de la credencial *Username with password* `webhook_token_credential`; URL `https://<jenkins>/project/<job>`).
Variables: `branch_name` = `gitlabSourceBranch`; `repository_name` y `appcode` = último y penúltimo segmento de `gitlabSourceRepoHttpUrl`.
No se usa Generic Webhook Trigger.
En GitLab los repos están en `mexico/<appcode>/<repo>` (grupo `mexico`, subgrupo = appcode).
El step `disparar_pipeline_aplicacion(branch_name, repository_name, appcode)` lanza `mexico/<appcode>/<repo>/<rama>` si la rama es
de ambiente (`dev, qa, uat, release, main`), si no `mexico/<appcode>/<repo>/integration`, con parámetro `BRANCH`, `wait: false`.
Los proyectos en Jenkins tienen la misma estructura de carpetas y nombres que en GitLab.
Archivos: config `jte-pipeline-configurations-automations/webhook`, librería `jte-automations-pipeline-libraries/webhook`,
template `jte-pipelines-template/webhook/push_orchestrator.groovy`.

## 5.2 CI JBoss (`jboss-integration`)

Un template por job/rama en `jte-pipelines-template/jboss-integration/`: `default_integration` (job `integration`, cualquier otra rama),
`dev_`, `qa_`, `uat_`, `main_integration`. Flujo: checkout → build Maven → carpeta temporal en el servidor → copia del artefacto →
dispara el job de CD (`jboss-deployment`, por crear) y lo espera → borra la carpeta temporal. Se agregan las etapas una por una y el
usuario prueba cada una antes de pasar a la siguiente (probado: checkout en default, dev, qa y uat; build Maven; carpeta temporal en JBoss).
Configs por micro: archivos sueltos `jte-pipeline-configurations-automations/jboss/<micro>/<rama>-integration-config.groovy`
(default, dev, qa, uat; main pendiente). Cada job de Jenkins apunta a su archivo con la ruta de config del job (*Pipeline Configuration
from SCM*), no con un Base Directory + `pipeline_config.groovy`.
Librería `gitlab` (sin campos; `gitlab { }` vacío en la config): `checkout_code(rama_por_defecto)` clona `<GITLAB_URL>/<JOB_NAME sin el
último segmento>.git` en la rama `BRANCH` con la credencial `GITLAB_CREDENTIALS_ID` (propiedades de la carpeta `mexico` en Jenkins).

Checkstyle suppressions (mixto): `maven/resources/checkstyle/checkstyle-suppressions.xml` común; un micro con excepciones propias usa
`maven/resources/checkstyle/<component_name>/checkstyle-suppressions.xml`. `check_code_styles` lo escribe en el workspace y pasa
`-Dcheckstyle.suppressions.location`. Reemplaza al `file_list` de la librería bitbucket (el `sonar-project.properties` se verá con Sonar).

SonarQube (Community 9.9.8, sin ramas): librería `sonarqube` (`scan_code_quality`, `check_quality_gate`), solo en `default_integration`, después
de los unit tests y antes del build. Scanner de Maven (`sonar-maven-plugin:3.11.0.3922`) dentro de
`withSonarQubeEnv('Sonar_server_SCAN_SERVER')`; project key = `component_name` (= nombre del repo). Quality Gate con `waitForQualityGate`
vía webhook global de SonarQube a `https://<jenkins>/sonarqube-webhook/`; `enforce_quality_gates` decide FAILURE o UNSTABLE.
Propiedades del análisis centralizadas (los desarrolladores no las controlan): `sonarqube/resources/sonarqube/sonar-project.properties`
general (o `.../<component_name>/sonar-project.properties`), pasadas como `-D` (le ganan al pom); las de exclusión se fuerzan vacías si
no se definen; `sonar.*` en pom.xml, `.mvn/maven.config` / `.mvn/jvm.config` o un `sonar-project.properties` en el repo (lo ignora el
scanner de Maven) → aviso y UNSTABLE.
Cobertura: la mide el pipeline (clase `Test`: `jacoco:prepare-agent test` a `target/jte-jacoco/jacoco.exec`, borra `target/site/jacoco` y
genera `jacoco:report` solo desde ese exec). El jacoco-maven-plugin del micro no influye; si no se puede medir, Sonar muestra 0%.

Librería `jboss_ci` (lado CI, config del micro; dev, qa, uat, main; no en default): `create_temporary_folder` crea
`<JBOSS_TEMP_PATH>/<component_name>` en cada `jboss_ci.host` por `ssh -o BatchMode=yes <usuario>@<host>` (llave del nodo; usuario = propiedad de carpeta
`JBOSS_SSH_USER`, o `jboss_ci.user` como excepción); carpeta base = propiedad de carpeta `JBOSS_TEMP_PATH` (o `jboss_ci.path`
como excepción), la misma que leerá el CD; `jboss_temporary_path()` valida la ruta antes del `rm -rf`. `copy_artifact_on_server` copia
el único `*.<component_packaging>` de `artifact_workspace_location` con `scp`. Pendientes: `trigger_jboss_deploy_job`, `remove_temporary_folder` (en el finally). Hosts/usuario reales solo en Jenkins.
El job de CD (uno por servidor/aplicación, ej. `pais/jboss/app1/dev-deployment`) tendrá su propia config del SERVIDOR y la librería
`jboss_cd` (`path`, `host_deploy`, `user`...). Nombres con guion bajo: `jboss-ci { }` no es válido en el DSL de JTE.

Estado por etapa en GitLab: cada etapa usa `gitlab_stage('nombre') { ... }` (librería `gitlab`) en vez de `stage`; informa running →
success/failed/canceled (UNSTABLE → success en GitLab: no tiene "warning" y un failed tumbaría el pipeline; la advertencia queda en Jenkins). Conexión: `gitLabConnection(gitLabConnection: 'api-gitlab_connection_java_repositorie')`
(GitLab connection de System, credencial GitLab API token `api`) en `properties([...])` de cada template (no se marca
en la pantalla del job: `properties()` lo reemplazaría). No se usa propiedad de carpeta para la conexión.

## 6. Cómo probar

- No hay Jenkins en el entorno de desarrollo: revisar la sintaxis con cuidado y explicar cómo probar cada cambio.
- Probar primero los comandos a mano desde el nodo: `ssh <user>@<host> "sudo -n -u <usuario_jboss> <comando>"`.
- Primer despliegue real en un ambiente no productivo.

## 7. Cómo trabajar en este repo (instrucciones para Claude Code)

- Mantén la separación config / librería / template y el estándar de nombres.
- Antes de agregar un campo, añádelo a la config **y** a `library_config.groovy`.
- No inventes servidores, rutas ni comandos de despliegue: usa los que el usuario documente en las secciones `POR COMPLETAR`.
- Después de cada cambio: **commit y push** a la rama principal (autor `bquintero <bquintero56@gmail.com>`) cuando el repo tenga remoto,
  y **decir qué archivos se crearon, modificaron o borraron**, con lo que cambió en cada uno, para que el usuario pueda replicarlos.
