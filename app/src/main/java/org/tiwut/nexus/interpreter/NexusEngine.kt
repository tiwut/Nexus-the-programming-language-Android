package org.tiwut.nexus.interpreter

import kotlinx.coroutines.CancellationException
import kotlin.math.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class NexusBreakException : Exception("Break loop")

interface NexusUI {
    fun onOutput(text: String)
    suspend fun onInput(prompt: String): String
    suspend fun showGuiMsg(text: String)
    fun buildGuiWindow(title: String)
    fun buildGuiColor(color: String)
    fun buildGuiLabel(text: String)
    fun buildGuiButton(text: String, action: String)
    suspend fun showGuiRun(onAction: suspend (String) -> Unit)
}

class NexusEngine(
    private val ui: NexusUI,
    private val workingDir: String = "/data/data/org.tiwut.nexus.interpreter/files/"
) {
    val version = "4.0-ULTRA"
    val vars = mutableMapOf<String, String>()
    val funcs = mutableMapOf<String, List<String>>()

    init {
        vars["OS"] = "Windows"
        vars["VER"] = version
        vars["ENGINE"] = "CPP-ULTRA"
        vars["USER"] = "NexusUser"
    }

    private fun resolveFile(path: String) = if (path.startsWith("/")) File(path) else File(workingDir, path)

    private suspend fun callStdlib(mod: String, func: String, argsRaw: String): String {
        val args = argsRaw.split(",").map { resolveComplex(it.trim()) }.filter { it.isNotEmpty() }

        when (mod) {
            "api" -> {
                if (func == "type") return "PC"
                if (func == "engine") return "CPP"
                if (func == "is_web") return "0"
                if (func == "is_pc") return "1"
            }
            "gui" -> {
                when (func) {
                    "msg" -> {
                        if (args.isNotEmpty()) ui.showGuiMsg(args[0])
                        return ""
                    }
                    "window" -> {
                        if (args.isNotEmpty()) ui.buildGuiWindow(args[0])
                        return ""
                    }
                    "color" -> {
                        if (args.isNotEmpty()) ui.buildGuiColor(args[0])
                        return ""
                    }
                    "label" -> {
                        if (args.isNotEmpty()) ui.buildGuiLabel(args[0])
                        return ""
                    }
                    "button" -> {
                        if (args.size >= 2) ui.buildGuiButton(args[0], args[1])
                        return ""
                    }
                    "run" -> {
                        ui.showGuiRun { actionName ->
                            if (funcs.containsKey(actionName)) {
                                run(funcs[actionName]!!)
                            }
                        }
                        return ""
                    }
                }
            }
            "math" -> {
                if (func == "pi") return Math.PI.toString()
                if (func == "e") return Math.E.toString()
                if (func == "tau") return (Math.PI * 2).toString()
                
                if (args.isEmpty()) return "0"
                val v = args[0].toDoubleOrNull() ?: 0.0
                when (func) {
                    "sin" -> return sin(v).toString()
                    "cos" -> return cos(v).toString()
                    "tan" -> return tan(v).toString()
                    "sqrt" -> return sqrt(v).toString()
                    "abs" -> return abs(v).toString()
                    "rad" -> return Math.toRadians(v).toString()
                    "log" -> return ln(v).toString()
                    "log10" -> return log10(v).toString()
                    "log2" -> return log2(v).toString()
                    "pow" -> {
                        if (args.size > 1) {
                            val p = args[1].toDoubleOrNull() ?: 0.0
                            return v.pow(p).toString()
                        }
                    }
                }
            }
            "str" -> {
                if (args.isEmpty()) return ""
                val s = args[0]
                when (func) {
                    "len" -> return s.length.toString()
                    "upper" -> return s.uppercase()
                    "lower" -> return s.lowercase()
                    "reverse" -> return s.reversed()
                    "trim" -> return s.trim()
                    "isnum" -> return if (s.toDoubleOrNull() != null) "1" else "0"
                    "isalpha" -> return if (s.all { it.isLetter() }) "1" else "0"
                    "find" -> {
                        if (args.size > 1) return s.indexOf(args[1]).toString()
                    }
                    "repeat" -> {
                        if (args.size > 1) {
                            val count = args[1].toIntOrNull() ?: 0
                            return s.repeat(count)
                        }
                    }
                }
            }
            "sys" -> {
                when (func) {
                    "time" -> return (System.currentTimeMillis() / 1000).toString()
                    "exit" -> throw CancellationException("Nexus Execution Terminated (sys.exit)")
                    "os" -> return "Windows"
                    "user" -> return "NexusUser"
                    "cwd" -> return workingDir
                    "shell" -> {
                        if (args.isNotEmpty()) {
                            return try {
                                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", args[0]))
                                val result = process.inputStream.bufferedReader().readText()
                                process.waitFor()
                                result
                            } catch (e: Exception) {
                                "ERR"
                            }
                        }
                    }
                }
            }
            "io" -> {
                when (func) {
                    "read" -> {
                        if (args.isNotEmpty()) {
                            return try {
                                resolveFile(args[0]).readText()
                            } catch (e: Exception) {
                                "ERR"
                            }
                        }
                    }
                    "write" -> {
                        if (args.size >= 2) {
                            try {
                                resolveFile(args[0]).parentFile?.mkdirs()
                                resolveFile(args[0]).writeText(args[1])
                                return "1"
                            } catch (e: Exception) {
                                return "0"
                            }
                        }
                    }
                    "append" -> {
                        if (args.size >= 2) {
                            try {
                                resolveFile(args[0]).parentFile?.mkdirs()
                                resolveFile(args[0]).appendText(args[1])
                                return "1"
                            } catch (e: Exception) {
                                return "0"
                            }
                        }
                    }
                    "exists" -> {
                        if (args.isNotEmpty()) {
                            return if (resolveFile(args[0]).exists()) "1" else "0"
                        }
                    }
                    "size" -> {
                        if (args.isNotEmpty()) {
                            return resolveFile(args[0]).length().toString()
                        }
                    }
                    "ext" -> {
                        if (args.isNotEmpty()) {
                            return resolveFile(args[0]).extension
                        }
                    }
                }
            }
            "rnd" -> {
                if (func == "int" && args.size >= 2) {
                    val min = args[0].toIntOrNull() ?: 0
                    val max = args[1].toIntOrNull() ?: 100
                    return (min..max).random().toString()
                }
            }
            "date" -> {
                val now = Date()
                when (func) {
                    "now" -> return SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now)
                    "year" -> return SimpleDateFormat("yyyy").format(now)
                    "month" -> return SimpleDateFormat("MM").format(now)
                    "day" -> return SimpleDateFormat("dd").format(now)
                    "hour" -> return SimpleDateFormat("HH").format(now)
                    "min" -> return SimpleDateFormat("mm").format(now)
                    "sec" -> return SimpleDateFormat("ss").format(now)
                }
            }
        }
        return ""
    }

    private suspend fun evalCond(inputRaw: String): Boolean {
        val input = inputRaw.trim()
        val eq = input.indexOf("==")
        val lt = input.indexOf("<")
        val gt = input.indexOf(">")

        if (eq != -1) {
            val left = resolveComplex(input.substring(0, eq).trim())
            val right = resolveComplex(input.substring(eq + 2).trim())
            return left == right
        } else if (lt != -1) {
            val left = resolveComplex(input.substring(0, lt).trim()).toDoubleOrNull() ?: 0.0
            val right = resolveComplex(input.substring(lt + 1).trim()).toDoubleOrNull() ?: 0.0
            return left < right
        } else if (gt != -1) {
            val left = resolveComplex(input.substring(0, gt).trim()).toDoubleOrNull() ?: 0.0
            val right = resolveComplex(input.substring(gt + 1).trim()).toDoubleOrNull() ?: 0.0
            return left > right
        } else {
            val res = resolveComplex(input)
            return res != "0" && res.isNotEmpty()
        }
    }

    private suspend fun checkInputCmd(inputRaw: String): String? {
        val input = inputRaw.trim()
        if (input.startsWith("input ")) {
            val prompt = resolveComplex(input.substring(6))
            return ui.onInput(prompt)
        }
        return null
    }

    suspend fun resolveComplex(inputOrig: String): String {
        val input = inputOrig.trim()
        if (input.isEmpty()) return ""

        val inRes = checkInputCmd(input)
        if (inRes != null) return inRes

        val modRegex = Regex("""(\w+)\.(\w+)\((.*)\)""")
        val match = modRegex.find(input)
        if (match != null) {
            val (mod, func, args) = match.destructured
            return callStdlib(mod, func, args)
        }

        if (input.startsWith("\"") && input.endsWith("\"") && input.length >= 2) {
            return input.substring(1, input.length - 1)
        }

        if (vars.containsKey(input)) {
            return vars[input]!!
        }

        val plusIdx = input.indexOf('+')
        if (plusIdx != -1) {
            return resolveComplex(input.substring(0, plusIdx)) + resolveComplex(input.substring(plusIdx + 1))
        }

        val minusIdx = input.indexOf('-')
        if (minusIdx > 0 && input[minusIdx - 1] == ' ') {
            val left = resolveComplex(input.substring(0, minusIdx).trim()).toDoubleOrNull() ?: 0.0
            val right = resolveComplex(input.substring(minusIdx + 1).trim()).toDoubleOrNull() ?: 0.0
            return (left - right).toString()
        }
        
        val mulIdx = input.indexOf('*')
        if (mulIdx != -1) {
             val left = resolveComplex(input.substring(0, mulIdx).trim()).toDoubleOrNull() ?: 0.0
             val right = resolveComplex(input.substring(mulIdx + 1).trim()).toDoubleOrNull() ?: 0.0
             return (left * right).toString()
        }
        
        val divIdx = input.indexOf('/')
        if (divIdx != -1) {
             val left = resolveComplex(input.substring(0, divIdx).trim()).toDoubleOrNull() ?: 0.0
             val right = resolveComplex(input.substring(divIdx + 1).trim()).toDoubleOrNull() ?: 1.0
             return (left / right).toString()
        }

        return input
    }

    suspend fun runCode(code: String) {
        val lines = code.split("\n")
        try {
            run(lines)
        } catch (e: CancellationException) {
            ui.onOutput("[INFO] ${e.message}")
        } catch (e: Exception) {
            ui.onOutput("Error: ${e.message}")
        }
    }

    private suspend fun run(lines: List<String>) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("#")) {
                i++
                continue
            }

            if (line.startsWith("input ")) {
                val parts = line.substring(6).trim().split(" ", limit = 2)
                if (parts.size == 2 && !parts[0].startsWith("\"")) {
                    val varName = parts[0]
                    val promptStr = resolveComplex(parts[1])
                    vars[varName] = ui.onInput(promptStr)
                } else if (parts.size == 1) {
                    val promptStr = resolveComplex(parts[0])
                    ui.onInput(promptStr) 
                } else {
                    val promptStr = resolveComplex(line.substring(6))
                    ui.onInput(promptStr)
                }
            } else if (line.startsWith("set ")) {
                val eq = line.indexOf('=')
                if (eq != -1) {
                    val name = line.substring(4, eq).trim()
                    vars[name] = resolveComplex(line.substring(eq + 1))
                }
            } else if (line.startsWith("out ")) {
                ui.onOutput("Nexus › " + resolveComplex(line.substring(4)))
            } else if (line == "break") {
                throw NexusBreakException()
            } else if (line.startsWith("if ")) {
                val condRaw = line.substring(3)
                val cond = evalCond(condRaw)

                i++
                val start = i
                var d = 1
                var elsePos = -1

                while (d > 0 && i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("if ")) d++
                    else if (l == "else" && d == 1) elsePos = i
                    else if (l == "end" || l.startsWith("end ")) d--
                    
                    if (d == 0) break
                    i++
                }

                if (cond) {
                    val block = ArrayList<String>()
                    val endIdx = if (elsePos != -1) elsePos else i
                    for (j in start until endIdx) block.add(lines[j])
                    try { run(block) } catch(e: NexusBreakException) { throw e }
                } else if (elsePos != -1) {
                    val block = ArrayList<String>()
                    for (j in elsePos + 1 until i) block.add(lines[j])
                    try { run(block) } catch(e: NexusBreakException) { throw e }
                }
            } else if (line.startsWith("loop ")) {
                val countStr = resolveComplex(line.substring(5))
                val count = countStr.toDoubleOrNull()?.toInt() ?: 0

                i++
                val start = i
                var d = 1
                while (d > 0 && i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("loop ")) d++
                    else if (l == "end" || l.startsWith("end ")) d--
                    
                    if (d == 0) break
                    i++
                }

                val block = ArrayList<String>()
                for (j in start until i) {
                    block.add(lines[j])
                }
                for (c in 0 until count) {
                    try {
                        run(block)
                    } catch (e: NexusBreakException) {
                        break
                    }
                }
            } else if (line.startsWith("fn ")) {
                val lp = line.indexOf('(')
                if (lp != -1) {
                    val name = line.substring(3, lp).trim()
                    val block = ArrayList<String>()
                    i++
                    while (i < lines.size && lines[i].trim() != "end") {
                        block.add(lines[i])
                        i++
                    }
                    funcs[name] = block
                }
            } else if (line.contains('.') && line.contains('(')) {
                resolveComplex(line)
            } else if (line.contains("()")) {
                val name = line.substring(0, line.indexOf('(')).trim()
                if (funcs.containsKey(name)) {
                    run(funcs[name]!!)
                }
            }
            i++
        }
    }
}
