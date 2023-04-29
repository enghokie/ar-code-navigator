val COMMON_CLASS_REGEX = "(^class|\\sclass)\\s+\\w+(\\s|\\()+".toRegex()


data class ClassData (
    val name: String,
    var parentClasses: HashSet<String> = HashSet<String>(),
    var refClasses: HashSet<String> = HashSet<String>()
)

class CodeData {
    var classes = HashMap<String, ClassData>()

    override fun toString(): String {
        var classHierarchy: String = ""
        for (classData in classes.values) {
            classHierarchy += "${classData.name} class details:"
            classHierarchy += "\n\tParent classes:"
            for (parentClass in classData.parentClasses)
                classHierarchy += "\n\t\t$parentClass"

            classHierarchy += "\n\tReference classes:"
            for (refClass in classData.refClasses)
                classHierarchy += "\n\t\t$refClass"

            classHierarchy += "\n\n"
        }

        return classHierarchy
    }
}

fun findCppParentClass(codeString: String): String? {
    val compare1 = "public "
    val compare2 = "private "
    val parentIdx = when {
        codeString.contains(compare1) -> codeString.indexOf(compare1) + compare1.length
        codeString.contains(compare2) -> codeString.indexOf(compare2) + compare2.length
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf(' ', parentIdx))
}

fun findJavaParentClass(codeString: String): String? {
    val compare1 = "extends "
    val parentIdx = when {
        codeString.contains(compare1) -> codeString.indexOf(compare1) + compare1.length
        else -> -1
    }
    return if (parentIdx == -1) null else codeString.substring(parentIdx, codeString.indexOf(' ', parentIdx))
}

fun findKotlinParentClass(codeString: String): String? {
    val compare1 = " : public "
    val compare2 = ") : "
    val parentIdx = when {
        codeString.contains(compare1) -> codeString.indexOf(compare1) + compare1.length
        codeString.contains(compare2) -> codeString.indexOf(compare2) + compare2.length
        else -> -1
    }

    return if (parentIdx == -1) {
        null
    } else {
        val idx1 = codeString.indexOf('(', parentIdx)
        val idx2 = codeString.indexOf(' ', parentIdx)
        if (idx1 != -1) codeString.substring(parentIdx, idx1) else codeString.substring(parentIdx, idx2)
    }
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
    print(codeData.toString())
}
