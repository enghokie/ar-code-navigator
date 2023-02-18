import java.util.HashMap
import java.util.HashSet


val COMMON_CLASS_REGEX = "(^class|\\sclass)\\s+\\w+(\\s|\\()+".toRegex()


class ClassData (var name: String) {
    var parentClasses = HashSet<String>()
    var refClasses = HashSet<String>()
}


class CodeData {
    var classes = HashMap<String, ClassData>()
}

fun findCppParentClass(codeString: String): String? {
    val parentIdx = when {
        codeString.contains("public") -> codeString.indexOf("public") + "public".length + 1
        codeString.contains("private") -> codeString.indexOf("private") + "private".length + 1
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf(' ', parentIdx))
}


fun findJavaParentClass(codeString: String): String? {
    val parentIdx = when {
        codeString.contains("extends") -> codeString.indexOf("extends") + "extends".length + 1
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf(' ', parentIdx))
}


fun findKotlinParentClass(codeString: String): String? {
    val parentIdx = when {
        codeString.contains(") : ") -> codeString.indexOf(") : ") + ") : ".length
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf("(", parentIdx))
}


fun parseCode(language: String, codeText: String, codeData: CodeData): Boolean {
    var parsedCode = false
    var curClass: ClassData? = null
    val codeStrings = codeText.split('\n')
    codestring@ for (codeString in codeStrings) {
        var className = COMMON_CLASS_REGEX.find(codeString)?.value
        if (className !== null) {
            // Get class details
            className = className.drop(className.indexOf("class") + "class".length).trim().removeSuffix("(")
            if (!codeData.classes.containsKey(className)) {
                codeData.classes[className] = ClassData(className)

                // Check for parent classes
                val parentClassName = when(language.lowercase()) {
                    "cpp", "c++" -> findCppParentClass(codeString)
                    "java" -> findJavaParentClass(codeString)
                    "kotlin" -> findKotlinParentClass(codeString)
                    else -> throw Exception("No support for language $language")
                }
                if (parentClassName !== null) {
                    codeData.classes[className]?.parentClasses?.add(parentClassName)
                }
            }

            curClass = codeData.classes[className]
            parsedCode = true
            continue
        }
        else if (curClass === null) {
            continue
        }

        // Find references classes that we've encountered the declaration for already
        for (name in codeData.classes.keys) {
            val classNameIdx = codeString.indexOf(name)
            if (classNameIdx == -1) continue

            val endOfClassIdx = classNameIdx + name.length
            if (codeString.length >= endOfClassIdx + 1 && codeString[endOfClassIdx] == '(')
                continue@codestring

            curClass.refClasses.add(name)
            parsedCode = true
        }
    }

    return parsedCode
}


fun logClassHiararchy(codeData: CodeData) {
    for (classData in codeData.classes.values) {
        println("${classData.name} class details:")
        println("\tParent classes:")
        for (parentClass in classData.parentClasses) println("\t\t$parentClass")
        println("\tReference classes:")
        for (refClass in classData.refClasses) println("\t\t$refClass")
    }
}