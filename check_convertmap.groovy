import groovy.sql.Sql
import java.io.FileOutputStream

// grape stuff that is needed to avoid the classloader exception
// source:  http://www.techper.net/2010/04/19/groovy-grape-adding-dependencies-to-root-classloader/
def classLoader = this.getClass().getClassLoader()
while (!classLoader.getClass().getName().equals("org.codehaus.groovy.tools.RootLoader")) {
  classLoader = classLoader.getParent()
}

// force grape to use the root classloader - to ensure that Class.forName works for dependencies
groovy.grape.Grape.grab(group:'postgresql', module:'postgresql', version:'8.3-603.jdbc3', classLoader: classLoader)

// save a th01 map from wafermap to the disk
def saveTh01map(wafermap) {
    f = File.createTempFile("wmap", ".th01")
    fos = new FileOutputStream(f)
    fos.write(wafermap)
    fos.close()
    return f
}

// extract the filename from a convertmap stdout string
def extractFilename(stdout) {
    def m = stdout =~ /ResultFile '([a-zA-Z0-9\.\/\-]*)'/
    m[0][1]
}

// convert a th01 file
def convert(th01, format) {
    command = "convertMap input=${th01.absolutePath} outputFormat=${format} verbose"
    // println "executing ${command}"
    def proc = command.execute()
    proc.waitFor()
    def stdout = proc.in.text
    // println "rest ${stdout}"
    new File(extractFilename(stdout))
}

// compare the latest wafermaps
def compareLatestWafermaps(amount) {
    def sql = Sql.newInstance("jdbc:postgresql://postgresql.colo.elex.be/partner", "jboss", "jboss", "org.postgresql.Driver")
    def verified = 0
    def correct = 0
    def incorrect = 0

    sql.eachRow("""select * from wafermap
                   where wafermap is not null
                     and organization like '%AMKOR%'
                     and convertedwafermap is not null
                   order by id desc limit ${amount}""") {
        th01 = saveTh01map(it.wafermap)
        convertedFile = convert(th01, "Amkor")

        converted = new String(convertedFile.readBytes())
        original = new String(it.convertedwafermap)

        ok = converted == original
        println "${it.lotname}-${it.waferid} compare result: ${ok}"

        if (!ok) {
            println "CONVERTED:"
            println converted

            println "EXPECTED:"
            println original

            incorrect++
        } else {
            correct++
        }

        verified++
    }

    println "Verified ${verified} wafermaps.  ${correct} succeeded, ${incorrect} failed [${correct/verified*100}%]"
}

def amount = 10

if (args.length > 0) {
    amount = args[0].toInteger()
}

compareLatestWafermaps(amount)
//println extractFilename("""ClearWaferMap
//readMap::Wafermap '/tmp/wmap_1222697373457562959.th01' is loading yet...
//readMap::ready...
//ConvertMapMain ResultFile './T25042-19-C6'""")

