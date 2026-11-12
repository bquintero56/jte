// MANTENIMIENTO — job de Jenkins tipo "Pipeline" normal con "Pipeline script" (NO es JTE): se pega este archivo completo.
// Repara el enlace roto jre/lib/tzdb.dat de un JDK 8 de Red Hat instalado como tool de Jenkins (Extraer *.zip/*.tar.gz).
// Síntoma en los builds: java.lang.Error: java.io.FileNotFoundException: <JDK>/jre/lib/tzdb.dat (No such file or directory)
// Causa: en ese JDK tzdb.dat es un enlace a /usr/share/javazi-1.8/tzdb.dat (paquete tzdata-java, que el nodo no tiene);
//        el archivo original queda al lado como tzdb.dat.upstream.
// Qué hace: 1) Diagnóstico: usuario del agente, permisos de la carpeta, enlaces del JDK y cuáles están rotos (no cambia nada).
//           2) Reparación (solo con APPLY_FIX): reemplaza el enlace roto por una copia de tzdb.dat.upstream.
//           3) Verificación: comprueba que Java ya carga las zonas horarias.
// Corre con el usuario del agente de Jenkins, que es el dueño de la carpeta de tools (él la instaló).
// Arregla UN nodo por ejecución. La solución definitiva es corregir el archivo comprimido del JDK en Nexus.
properties([
    parameters([
        string(name: 'NODE_LABEL', defaultValue: '', trim: true,
               description: 'Nombre (o etiqueta) del nodo a revisar. Sale al inicio del log del build que falló: "Running on <nodo>".'),
        string(name: 'JDK_HOME', defaultValue: '/jenkins/tools/hudson.model.JDK/jdk-1.8.0.412/jdk-1.8.0.412', trim: true,
               description: 'Carpeta del JDK en el nodo (la que aparece en el error, sin /jre/lib/tzdb.dat).'),
        booleanParam(name: 'APPLY_FIX', defaultValue: false,
                     description: 'Sin marcar: solo diagnóstico (no cambia nada). Marcado: repara el enlace roto de tzdb.dat.')
    ]),
    disableConcurrentBuilds()
])

def node_label = String.valueOf(params.NODE_LABEL ?: '').trim()
def jdk_home   = String.valueOf(params.JDK_HOME ?: '').trim().replaceFirst(/\/+$/, '')
def apply_fix  = (params.APPLY_FIX == true)

if (!node_label) {
    error('[ERROR]: ❌ Falta NODE_LABEL. Ejecuta el job con "Build with Parameters" e indica el nodo (la primera ejecución solo registra los parámetros).')
}
if (!(jdk_home ==~ /\/[A-Za-z0-9._\/-]+/) || jdk_home.contains('..')) {
    error("[ERROR]: ❌ JDK_HOME no es una ruta absoluta válida: '${jdk_home}'")
}

// Prueba real: pide a Java la zona horaria (lo mismo que hace Maven al arrancar). Usa jrunscript, que viene en el JDK 8.
def timezone_test = '''
if [ -x "$JDK_HOME/bin/jrunscript" ]; then
    if salida=$("$JDK_HOME/bin/jrunscript" -e 'print(java.util.TimeZone.getDefault().getID())' 2>&1); then
        echo "[INFO]: ✅ Java carga las zonas horarias (zona del nodo: $salida)"
    else
        echo "[WARN]: ⚠️ Java NO puede cargar las zonas horarias:"
        echo "$salida" | head -5
        JAVA_TZ_OK=no
    fi
else
    echo "[WARN]: ⚠️ No existe $JDK_HOME/bin/jrunscript: no se pudo probar Java (prueba con el build del micro)"
fi
'''

timeout(time: 10, unit: 'MINUTES') {
    node(node_label) {
        withEnv(["JDK_HOME=${jdk_home}"]) {

            stage('Diagnóstico') {
                sh('''#!/bin/sh
LIB="$JDK_HOME/jre/lib"
echo "[INFO]: Nodo: $(hostname) | Usuario del agente: $(whoami)"
echo "[INFO]: Grupos del usuario: $(id)"

if [ ! -d "$LIB" ]; then
    echo "[ERROR]: ❌ No existe $LIB en este nodo (revisa JDK_HOME y NODE_LABEL)"
    exit 1
fi

echo "[INFO]: Permisos de las carpetas (columna 1 = permisos, 3 = dueño, 4 = grupo):"
ls -ld "$JDK_HOME" "$LIB"

echo "[INFO]: tzdb.dat y su original:"
ls -l "$LIB"/tzdb.dat* 2>&1

if [ -w "$LIB" ]; then
    echo "[INFO]: ✅ El usuario $(whoami) SÍ puede modificar $LIB"
else
    echo "[WARN]: ⚠️ El usuario $(whoami) NO puede modificar $LIB (la reparación fallaría)"
fi

echo "[INFO]: Todos los enlaces simbólicos del JDK:"
find "$JDK_HOME" -type l -exec ls -l {} +

echo "[INFO]: Enlaces ROTOS del JDK (apuntan a algo que no existe en este nodo):"
rotos=$(find "$JDK_HOME" -xtype l)
if [ -z "$rotos" ]; then
    echo "[INFO]: ✅ ninguno"
else
    echo "$rotos"
fi
''' + timezone_test)
            }

            if (apply_fix) {
                stage('Reparar tzdb.dat') {
                    sh('''#!/bin/sh
LIB="$JDK_HOME/jre/lib"

if [ -e "$LIB/tzdb.dat" ]; then
    echo "[INFO]: ✅ tzdb.dat ya es válido: no se cambió nada"
    exit 0
fi
if [ ! -f "$LIB/tzdb.dat.upstream" ]; then
    echo "[ERROR]: ❌ No existe $LIB/tzdb.dat.upstream: no hay de dónde restaurar tzdb.dat"
    exit 1
fi

rm -f "$LIB/tzdb.dat" && cp -p "$LIB/tzdb.dat.upstream" "$LIB/tzdb.dat" || {
    echo "[ERROR]: ❌ No se pudo reemplazar $LIB/tzdb.dat (revisa los permisos del diagnóstico)"
    exit 1
}
echo "[INFO]: ✅ Enlace roto reemplazado por una copia de tzdb.dat.upstream"
''')
                }

                stage('Verificación') {
                    sh('''#!/bin/sh
LIB="$JDK_HOME/jre/lib"
ls -l "$LIB"/tzdb.dat*
JAVA_TZ_OK=si
''' + timezone_test + '''
if [ ! -f "$LIB/tzdb.dat" ] || [ "$JAVA_TZ_OK" = "no" ]; then
    echo "[ERROR]: ❌ tzdb.dat sigue sin funcionar"
    exit 1
fi
echo "[INFO]: ✅ JDK reparado en este nodo. Vuelve a ejecutar el build del micro."
''')
                }
            }
            else {
                println('[INFO]: Solo diagnóstico: no se cambió nada. Para reparar, ejecuta de nuevo con APPLY_FIX marcado.')
            }
        }
    }
}
