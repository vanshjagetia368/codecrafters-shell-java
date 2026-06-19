import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
    
    static class Job {
        int id;
        Process process; 
        String command;  
        String status;

        public Job(int id, Process process, String command, String status) {
            this.id = id;
            this.process = process;
            this.command = command;
            this.status = status;
        }
    }

    private static final Map<String, String> completionRegistry = new HashMap<>();
    private static final List<Job> backgroundJobs = new ArrayList<>();
    private static final String[] builtins = {"echo", "exit", "type", "complete", "jobs", "pwd", "cd"};
    
    private static String findLongestCommonPrefix(List<String> strs) {
        if (strs == null || strs.isEmpty()) return "";
        String prefix = strs.get(0);
        for (int i = 1; i < strs.size(); i++) {
            while (strs.get(i).indexOf(prefix) != 0) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) return "";
            }
        }
        return prefix;
    }

    private static List<String> parseCommand(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append('\\');
                        current.append(next);
                        i++;
                    }
                } else {
                    current.append('\\');
                }
            } else if (!inSingleQuotes && !inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args;
    }

    private static void reapJobs() {
        int totalJobs = backgroundJobs.size();
        List<Job> jobsToRemove = new ArrayList<>();

        for (int i = 0; i < totalJobs; i++) {
            Job job = backgroundJobs.get(i);
            
            if (!job.process.isAlive()) {
                job.status = "Done";
                jobsToRemove.add(job);

                char marker = ' ';
                if (i == totalJobs - 1) {
                    marker = '+';
                } else if (i == totalJobs - 2) {
                    marker = '-';
                }

                System.out.printf("[%d]%c  %-24s%s%n", job.id, marker, job.status, job.command);
                System.out.flush();
            }
        }
        
        backgroundJobs.removeAll(jobsToRemove);
    }

    private static File findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        String[] pathsList = pathEnv.split(File.pathSeparator);
        for (String dir : pathsList) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    private static boolean isBuiltinCmd(String cmd) {
        for (String b : builtins) {
            if (b.equals(cmd)) return true;
        }
        return false;
    }

    private static void executeCommandBlock(List<String> parts, InputStream pipelineIn, PrintStream pipelineOut, boolean isBackgroundJob, String rawCommandText) throws Exception {
        String stdoutFile = null;
        String stderrFile = null;
        boolean appendStdout = false;
        boolean appendStderr = false;

        for (int i = 0; i < parts.size(); i++) {
            String token = parts.get(i);
            if (token.equals(">") || token.equals("1>")) {
                if (i + 1 < parts.size()) { stdoutFile = parts.get(i + 1); appendStdout = false; }
                List<String> newParts = new ArrayList<>(parts.subList(0, i));
                if (i + 2 < parts.size()) newParts.addAll(parts.subList(i + 2, parts.size()));
                parts = newParts; i--;
            } 
            else if (token.equals(">>") || token.equals("1>>")) {
                if (i + 1 < parts.size()) { stdoutFile = parts.get(i + 1); appendStdout = true; }
                List<String> newParts = new ArrayList<>(parts.subList(0, i));
                if (i + 2 < parts.size()) newParts.addAll(parts.subList(i + 2, parts.size()));
                parts = newParts; i--;
            } 
            else if (token.equals("2>")) {
                if (i + 1 < parts.size()) { stderrFile = parts.get(i + 1); appendStderr = false; }
                List<String> newParts = new ArrayList<>(parts.subList(0, i));
                if (i + 2 < parts.size()) newParts.addAll(parts.subList(i + 2, parts.size()));
                parts = newParts; i--;
            } 
            else if (token.equals("2>>")) {
                if (i + 1 < parts.size()) { stderrFile = parts.get(i + 1); appendStderr = true; }
                List<String> newParts = new ArrayList<>(parts.subList(0, i));
                if (i + 2 < parts.size()) newParts.addAll(parts.subList(i + 2, parts.size()));
                parts = newParts; i--;
            }
        }

        if (parts.isEmpty()) return;
        String command = parts.get(0);

        PrintStream outTarget = pipelineOut;
        if (stdoutFile != null) {
            outTarget = new PrintStream(new FileOutputStream(stdoutFile, appendStdout), true);
        }

        if (isBuiltinCmd(command)) {
            if (stderrFile != null) {
                try (FileOutputStream fos = new FileOutputStream(stderrFile, appendStderr)) {
                    // Touches/initializes target error redirection targets for built-ins
                }
            }

            if (command.equals("exit")) {
                String[] setCooked = {"stty", "sane", "-F", "/dev/tty"};
                Runtime.getRuntime().exec(setCooked).waitFor();
                System.exit(0);
            }
            else if (command.equals("pwd")) {
                String currentDir = System.getProperty("user.dir");
                outTarget.print(currentDir + System.lineSeparator());
                outTarget.flush();
            }
            else if (command.equals("cd")) {
                if (parts.size() >= 2) {
                    String pathArg = parts.get(1);
                    File targetDir = new File(pathArg);

                    if (targetDir.exists() && targetDir.isDirectory()) {
                        System.setProperty("user.dir", targetDir.getAbsolutePath());
                    } else {
                        outTarget.print("cd: " + pathArg + ": No such file or directory" + System.lineSeparator());
                        outTarget.flush();
                    }
                }
            }
            else if (command.equals("echo")) {
                StringBuilder output = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) output.append(" ");
                    output.append(parts.get(i));
                }
                outTarget.print(output.toString() + System.lineSeparator());
                outTarget.flush();
            }
            else if (command.equals("type")) {
                if (parts.size() >= 2) {
                    String cmd = parts.get(1);
                    String result = cmd + ": not found";
                    if (isBuiltinCmd(cmd)) {
                        result = cmd + " is a shell builtin";
                    } else {
                        File file = findExecutable(cmd);
                        if (file != null) {
                            result = cmd + " is " + file.getAbsolutePath();
                        }
                    }
                    outTarget.print(result + System.lineSeparator());
                    outTarget.flush();
                }
            }
            else if (command.equals("complete")) {
                String result = null;
                if (parts.size() >= 4 && parts.get(1).equals("-C")) {
                    completionRegistry.put(parts.get(3), parts.get(2));
                } else if (parts.size() >= 3 && parts.get(1).equals("-r")) {
                    completionRegistry.remove(parts.get(2));
                } else if (parts.size() >= 3 && parts.get(1).equals("-p")) {
                    String targetCmd = parts.get(2);
                    if (completionRegistry.containsKey(targetCmd)) {
                        result = "complete -C '" + completionRegistry.get(targetCmd) + "' " + targetCmd + System.lineSeparator();
                    } else {
                        result = "complete: " + targetCmd + ": no completion specification" + System.lineSeparator();
                    }
                }
                if (result != null) {
                    outTarget.print(result);
                    outTarget.flush();
                }
            }
            else if (command.equals("jobs")) {
                StringBuilder jobsOutput = new StringBuilder();
                int totalJobs = backgroundJobs.size();
                List<Job> jobsToRemove = new ArrayList<>();

                for (Job job : backgroundJobs) {
                    if (!job.process.isAlive()) {
                        job.status = "Done";
                        jobsToRemove.add(job);
                    }
                }
                for (int i = 0; i < totalJobs; i++) {
                    Job job = backgroundJobs.get(i);
                    char marker = (i == totalJobs - 1) ? '+' : ((i == totalJobs - 2) ? '-' : ' ');
                    String commandStr = job.status.equals("Running") ? job.command + " &" : job.command;
                    jobsOutput.append(String.format("[%d]%c  %-24s%s", job.id, marker, job.status, commandStr)).append(System.lineSeparator());
                }
                backgroundJobs.removeAll(jobsToRemove);
                outTarget.print(jobsOutput.toString());
                outTarget.flush();
            }

            if (stdoutFile != null) outTarget.close();
        } else {
            File executableFile = findExecutable(command);
            if (executableFile == null) {
                System.out.println(command + ": command not found");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(parts);
            Map<String, String> env = pb.environment();
            
            // Sync current runtime working directory to spawned external processes
            pb.directory(new File(System.getProperty("user.dir")));
            env.put("PATH", executableFile.getParent() + File.pathSeparator + env.getOrDefault("PATH", ""));

            if (pipelineIn != System.in) {
                pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            }
            if (outTarget != System.out) {
                if (stdoutFile != null) {
                    pb.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(new File(stdoutFile)) : ProcessBuilder.Redirect.to(new File(stdoutFile)));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                }
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (stderrFile != null) {
                pb.redirectError(appendStderr ? ProcessBuilder.Redirect.appendTo(new File(stderrFile)) : ProcessBuilder.Redirect.to(new File(stderrFile)));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = pb.start();

            if (pipelineIn != System.in) {
                Thread pumpThread = new Thread(() -> {
                    try (OutputStream os = process.getOutputStream()) {
                        pipelineIn.transferTo(os);
                    } catch (Exception e) {}
                });
                pumpThread.start();
            }

            if (outTarget != System.out && stdoutFile == null) {
                try (InputStream is = process.getInputStream()) {
                    is.transferTo(outTarget);
                }
            }

            if (isBackgroundJob) {
                int currentJobId = 1;
                if (!backgroundJobs.isEmpty()) {
                    int maxId = 0;
                    for (Job j : backgroundJobs) {
                        if (j.id > maxId) maxId = j.id;
                    }
                    currentJobId = maxId + 1;
                }

                System.out.println("[" + currentJobId + "] " + process.pid());
                System.out.flush();
                
                backgroundJobs.add(new Job(currentJobId, process, rawCommandText, "Running"));
            } else {
                process.waitFor();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String[] setRaw = {"stty", "-icanon", "-echo", "min", "1", "-F", "/dev/tty"};
        Runtime.getRuntime().exec(setRaw).waitFor();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                String[] setCooked = {"stty", "sane", "-F", "/dev/tty"};
                Runtime.getRuntime().exec(setCooked).waitFor();
            } catch (Exception e) { /* Ignore */ }
        }));

        InputStream in = System.in;

        while (true) {
            reapJobs();

            System.out.print("$ ");
            System.out.flush();

            StringBuilder inputBuilder = new StringBuilder();
            int consecutiveTabs = 0;

            while (true) {
                int readByte = in.read();
                if (readByte == -1) break;
                char c = (char) readByte;

                if (c == '\n' || c == '\r') {
                    consecutiveTabs = 0;
                    System.out.println();
                    break;
                }
                else if (c == '\t') {
                    consecutiveTabs++;
                    String currentInput = inputBuilder.toString();
                    
                    if (!currentInput.isEmpty()) {
                        List<String> tempParts = parseCommand(currentInput);
                        boolean baseCommandHasSpace = currentInput.contains(" ");
                        
                        if (baseCommandHasSpace && !tempParts.isEmpty() && completionRegistry.containsKey(tempParts.get(0))) {
                            String baseCmd = tempParts.get(0);
                            String scriptPath = completionRegistry.get(baseCmd);
                            
                            String argv1 = baseCmd;
                            String argv2 = "";
                            String argv3 = "";

                            if (currentInput.endsWith(" ")) {
                                argv2 = "";
                                argv3 = tempParts.get(tempParts.size() - 1);
                            } else {
                                if (tempParts.size() >= 2) {
                                    argv2 = tempParts.get(tempParts.size() - 1);
                                    argv3 = tempParts.get(tempParts.size() - 2);
                                } else {
                                    argv2 = tempParts.get(tempParts.size() - 1);
                                    argv3 = "";
                                }
                            }

                            try {
                                List<String> cmdList = new ArrayList<>();
                                cmdList.add(scriptPath);
                                cmdList.add(argv1);
                                cmdList.add(argv2);
                                cmdList.add(argv3);

                                ProcessBuilder pb = new ProcessBuilder(cmdList);
                                Map<String, String> env = pb.environment();
                                env.put("COMP_LINE", currentInput);
                                env.put("COMP_POINT", String.valueOf(currentInput.getBytes().length));

                                Process process = pb.start();
                                process.waitFor();
                                
                                List<String> scriptCandidates = new ArrayList<>();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        if (!line.trim().isEmpty()) {
                                            scriptCandidates.add(line.trim());
                                        }
                                    }
                                }

                                Collections.sort(scriptCandidates);

                                if (scriptCandidates.isEmpty()) {
                                    System.out.print("\u0007");
                                    System.out.flush();
                                    consecutiveTabs = 0;
                                } 
                                else if (scriptCandidates.size() == 1) {
                                    String candidate = scriptCandidates.get(0);
                                    String suffix = candidate.substring(argv2.length()) + " ";
                                    inputBuilder.append(suffix);
                                    System.out.print(suffix);
                                    System.out.flush();
                                    consecutiveTabs = 0;
                                } 
                                else {
                                    String lcp = findLongestCommonPrefix(scriptCandidates);
                                    
                                    if (lcp.length() > argv2.length()) {
                                        String completedText = lcp.substring(argv2.length());
                                        inputBuilder.append(completedText);
                                        System.out.print(completedText);
                                        System.out.flush();
                                        consecutiveTabs = 0; 
                                    } else {
                                        if (consecutiveTabs == 1) {
                                            System.out.print("\u0007");
                                            System.out.flush();
                                        } else if (consecutiveTabs >= 2) {
                                            System.out.println();
                                            System.out.println(String.join("  ", scriptCandidates));
                                            System.out.print("$ " + currentInput);
                                            System.out.flush();
                                        }
                                    }
                                }
                                continue; 
                            } catch (Exception e) { /* Ignore */ }
                        }

                        Set<String> candidatesSet = new LinkedHashSet<>();
                        boolean isArgumentCompletion = currentInput.contains(" ");
                        String partialToken = "";
                        String matchPrefix = "";
                        String targetDirPath = System.getProperty("user.dir"); 

                        if (isArgumentCompletion) {
                            int lastSpaceIdx = currentInput.lastIndexOf(' ');
                            partialToken = currentInput.substring(lastSpaceIdx + 1);
                            
                            if (partialToken.contains("/")) {
                                int lastSlashIdx = partialToken.lastIndexOf('/');
                                targetDirPath = partialToken.substring(0, lastSlashIdx + 1);
                                matchPrefix = partialToken.substring(lastSlashIdx + 1);
                                
                                File targetDir = new File(targetDirPath);
                                if (targetDir.exists() && targetDir.isDirectory()) {
                                    File[] files = targetDir.listFiles();
                                    if (files != null) {
                                        for (File file : files) {
                                            if (file.getName().startsWith(matchPrefix)) {
                                                candidatesSet.add(file.getName());
                                            }
                                        }
                                    }
                                }
                            } else {
                                matchPrefix = partialToken;
                                File currentDir = new File(targetDirPath);
                                File[] files = currentDir.listFiles();
                                if (files != null) {
                                    for (File file : files) {
                                        if (file.getName().startsWith(matchPrefix)) {
                                            candidatesSet.add(file.getName());
                                        }
                                    }
                                }
                            }
                        } else {
                            partialToken = currentInput;
                            matchPrefix = currentInput;
                            for (String builtin : builtins) {
                                if (builtin.startsWith(matchPrefix)) {
                                    candidatesSet.add(builtin);
                                }
                            }
                            
                            String pathEnv = System.getenv("PATH");
                            if (pathEnv != null) {
                                String[] paths = pathEnv.split(File.pathSeparator);
                                for (String dirPath : paths) {
                                    File dir = new File(dirPath);
                                    if (dir.exists() && dir.isDirectory()) {
                                        File[] files = dir.listFiles();
                                        if (files != null) {
                                            for (File file : files) {
                                                if (file.isFile() && file.canExecute() && file.getName().startsWith(matchPrefix)) {
                                                    candidatesSet.add(file.getName());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        List<String> candidates = new ArrayList<>(candidatesSet);
                        Collections.sort(candidates);

                        if (candidates.size() == 1) {
                            String matched = candidates.get(0);
                            String suffix = " "; 
                            
                            if (isArgumentCompletion) {
                                File matchFile = new File(targetDirPath, matched);
                                if (matchFile.isDirectory()) suffix = "/";
                            }
                            
                            String completedText = matched.substring(matchPrefix.length()) + suffix;
                            inputBuilder.append(completedText);
                            System.out.print(completedText);
                            System.out.flush();
                            consecutiveTabs = 0;
                        } 
                        else if (candidates.size() > 1) {
                            String lcp = findLongestCommonPrefix(candidates);
                            
                            if (lcp.length() > matchPrefix.length()) {
                                String completedText = lcp.substring(matchPrefix.length());
                                inputBuilder.append(completedText);
                                System.out.print(completedText);
                                System.out.flush();
                                consecutiveTabs = 0; 
                            } else {
                                if (consecutiveTabs == 1) {
                                    System.out.print("\u0007");
                                    System.out.flush();
                                } else if (consecutiveTabs >= 2) {
                                    List<String> displayedCandidates = new ArrayList<>();
                                    for (String cand : candidates) {
                                        if (isArgumentCompletion && new File(targetDirPath, cand).isDirectory()) {
                                            displayedCandidates.add(cand + "/");
                                        } else {
                                            displayedCandidates.add(cand);
                                        }
                                    }
                                    System.out.println(); 
                                    System.out.println(String.join("  ", displayedCandidates));
                                    System.out.print("$ " + inputBuilder.toString());
                                    System.out.flush();
                                }
                            }
                        } else {
                            System.out.print("\u0007");
                            System.out.flush();
                            consecutiveTabs = 0;
                        }
                    } else {
                        System.out.print("\u0007");
                        System.out.flush();
                        consecutiveTabs = 0;
                    }
                }
                else if (readByte == 127 || c == '\b') {
                    consecutiveTabs = 0;
                    if (inputBuilder.length() > 0) {
                        inputBuilder.deleteCharAt(inputBuilder.length() - 1);
                        System.out.print("\b \b");
                        System.out.flush();
                    }
                }
                else {
                    consecutiveTabs = 0;
                    inputBuilder.append(c);
                    System.out.print(c);
                    System.out.flush();
                }
            }

            String input = inputBuilder.toString();
            List<String> parts = parseCommand(input);

            if (parts.isEmpty()) continue;

            boolean isBackgroundJob = parts.get(parts.size() - 1).equals("&");
            if (isBackgroundJob) parts.remove(parts.size() - 1);

            if (parts.isEmpty()) continue;

            String rawCommandText = String.join(" ", parts);

            int pipeIdx = parts.indexOf("|");
            if (pipeIdx != -1) {
                try {
                    List<List<String>> pipelineStages = new ArrayList<>();
                    List<String> currentStage = new ArrayList<>();
                    for (String part : parts) {
                        if (part.equals("|")) {
                            if (!currentStage.isEmpty()) {
                                pipelineStages.add(currentStage);
                                currentStage = new ArrayList<>();
                            }
                        } else {
                            currentStage.add(part);
                        }
                    }
                    if (!currentStage.isEmpty()) {
                        pipelineStages.add(currentStage);
                    }

                    boolean hasBuiltin = false;
                    for (List<String> stage : pipelineStages) {
                        if (!stage.isEmpty() && isBuiltinCmd(stage.get(0))) {
                            hasBuiltin = true;
                            break;
                        }
                    }

                    if (!hasBuiltin) {
                        List<ProcessBuilder> builders = new ArrayList<>();
                        for (List<String> stage : pipelineStages) {
                            String cmd = stage.get(0);
                            File exec = findExecutable(cmd);
                            if (exec == null) {
                                System.out.println(cmd + ": command not found");
                                builders = null;
                                break;
                            }
                            ProcessBuilder pb = new ProcessBuilder(stage);
                            pb.directory(new File(System.getProperty("user.dir")));
                            pb.environment().put("PATH", exec.getParent() + File.pathSeparator + pb.environment().getOrDefault("PATH", ""));
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            builders.add(pb);
                        }

                        if (builders != null && !builders.isEmpty()) {
                            builders.get(builders.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            List<Process> processes = ProcessBuilder.startPipeline(builders);
                            processes.get(processes.size() - 1).waitFor();
                        }
                    } else {
                        InputStream currentIn = System.in;
                        for (int i = 0; i < pipelineStages.size(); i++) {
                            List<String> stage = pipelineStages.get(i);
                            boolean isLast = (i == pipelineStages.size() - 1);

                            if (isLast) {
                                executeCommandBlock(stage, currentIn, System.out, false, "");
                            } else {
                                ByteArrayOutputStream nextOutStream = new ByteArrayOutputStream();
                                PrintStream printTarget = new PrintStream(nextOutStream);
                                
                                executeCommandBlock(stage, currentIn, printTarget, false, "");
                                printTarget.flush();
                                
                                currentIn = new java.io.ByteArrayInputStream(nextOutStream.toByteArray());
                            }
                        }
                    }
                } catch (Exception e) { /* Handle Pipeline Errors */ }
                continue;
            }

            try {
                executeCommandBlock(parts, System.in, System.out, isBackgroundJob, rawCommandText);
            } catch (Exception e) { /* Handle Command Execution Failures */ }
        }
    }
}