import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
    
    // Simple structural container to maintain background job metadata
    static class Job {
        int id;
        long pid;
        String command;
        String status;

        public Job(int id, long pid, String command, String status) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
        }
    }

    private static final Map<String, String> completionRegistry = new HashMap<>();
    // Tracks active background tasks globally
    private static final List<Job> backgroundJobs = new ArrayList<>();
    private static int nextJobId = 1;
    
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

    public static void main(String[] args) throws Exception {
        String[] builtins = {"echo", "exit", "type", "complete", "jobs"};

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            StringBuilder inputBuilder = new StringBuilder();
            int consecutiveTabs = 0;

            String[] setRaw = {"stty", "-icanon", "-echo", "min", "1", "-F", "/dev/tty"};
            Runtime.getRuntime().exec(setRaw).waitFor();

            InputStream in = System.in;

            while (true) {
                int readByte = in.read();
                if (readByte == -1) {
                    break;
                }

                char c = (char) readByte;

                if (c == '\n' || c == '\r') {
                    consecutiveTabs = 0;
                    String[] setCooked = {"stty", "sane", "-F", "/dev/tty"};
                    Runtime.getRuntime().exec(setCooked).waitFor();
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
                            } catch (Exception e) {
                                // Fallback
                            }
                        }

                        // Standard Fallback Autocompletion Flow
                        Set<String> candidatesSet = new LinkedHashSet<>();
                        boolean isArgumentCompletion = currentInput.contains(" ");
                        String partialToken = "";
                        String matchPrefix = "";
                        String targetDirPath = "."; 

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
                                if (matchFile.isDirectory()) {
                                    suffix = "/";
                                }
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

            if (parts.isEmpty()) {
                continue;
            }

            boolean isBackgroundJob = parts.get(parts.size() - 1).equals("&");
            if (isBackgroundJob) {
                parts.remove(parts.size() - 1);
            }

            if (parts.isEmpty()) {
                continue;
            }

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

            if (parts.isEmpty()) {
                continue;
            }

            String command = parts.get(0);

            if (command.equals("exit")) {
                break;
            }

            else if (command.equals("echo")) {
                StringBuilder output = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) output.append(" ");
                    output.append(parts.get(i));
                }
                String result = output.toString() + System.lineSeparator();

                if (stdoutFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(stdoutFile, appendStdout)) {
                        fos.write(result.getBytes());
                    }
                } else {
                    System.out.print(result);
                }

                if (stderrFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(stderrFile, appendStderr)) {
                        fos.flush();
                    }
                }
                continue;
            }

            else if (command.equals("type")) {
                if (parts.size() < 2) continue;
                String cmd = parts.get(1);
                String result;

                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("complete") || cmd.equals("jobs")) {
                    result = cmd + " is a shell builtin";
                } else {
                    result = cmd + ": not found";
                    String[] pathsList = System.getenv("PATH").split(File.pathSeparator);
                    for (String dir : pathsList) {
                        File file = new File(dir, cmd);
                        if (file.exists() && file.canExecute()) {
                            result = cmd + " is " + file.getAbsolutePath();
                            break;
                        }
                    }
                }

                if (stdoutFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(stdoutFile, appendStdout)) {
                        fos.write((result + System.lineSeparator()).getBytes());
                    }
                } else {
                    System.out.print(result + System.lineSeparator());
                }

                if (stderrFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(stderrFile, appendStderr)) {
                        fos.flush();
                    }
                }
                continue;
            }
            
            else if (command.equals("complete")) {
                String result = null;

                if (parts.size() >= 4 && parts.get(1).equals("-C")) {
                    String scriptPath = parts.get(2);
                    String targetCmd = parts.get(3);
                    completionRegistry.put(targetCmd, scriptPath);
                } 
                else if (parts.size() >= 3 && parts.get(1).equals("-r")) {
                    String targetCmd = parts.get(2);
                    completionRegistry.remove(targetCmd);
                }
                else if (parts.size() >= 3 && parts.get(1).equals("-p")) {
                    String targetCmd = parts.get(2);
                    if (completionRegistry.containsKey(targetCmd)) {
                        String path = completionRegistry.get(targetCmd);
                        result = "complete -C '" + path + "' " + targetCmd + System.lineSeparator();
                    } else {
                        result = "complete: " + targetCmd + ": no completion specification" + System.lineSeparator();
                    }
                }

                if (result != null) {
                    if (stdoutFile != null) {
                        try (FileOutputStream fos = new FileOutputStream(stdoutFile, appendStdout)) {
                            fos.write(result.getBytes());
                        }
                    } else {
                        System.out.print(result);
                    }
                }
                continue;
            }

            // Implement active jobs printing matching format criteria
            else if (command.equals("jobs")) {
                StringBuilder jobsOutput = new StringBuilder();
                for (Job job : backgroundJobs) {
                    // %-24s pads "Running" with right-side spaces to match 24 chars exact width
                    jobsOutput.append(String.format("[%d]+  %-24s%s", job.id, job.status, job.command))
                              .append(System.lineSeparator());
                }
                
                String outText = jobsOutput.toString();
                if (stdoutFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(stdoutFile, appendStdout)) {
                        fos.write(outText.getBytes());
                    }
                } else {
                    System.out.print(outText);
                }
                continue;
            }

            boolean foundExecutable = false;
            File executableFile = null;
            String[] pathsList = System.getenv("PATH").split(File.pathSeparator);
            for (String dir : pathsList) {
                File file = new File(dir, command);
                if (file.exists() && file.canExecute()) {
                    executableFile = file;
                    foundExecutable = true;
                    break;
                }
            }

            if (!foundExecutable) {
                System.out.println(command + ": command not found");
                continue;
            }

            if (command.contains(" ")) {
                parts.set(0, executableFile.getAbsolutePath());
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(parts);
                
                if (stdoutFile != null) {
                    pb.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(new File(stdoutFile)) : ProcessBuilder.Redirect.to(new File(stdoutFile)));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
                
                if (stderrFile != null) {
                    pb.redirectError(appendStderr ? ProcessBuilder.Redirect.appendTo(new File(stderrFile)) : ProcessBuilder.Redirect.to(new File(stderrFile)));
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process process = pb.start();

                if (isBackgroundJob) {
                    System.out.println("[" + nextJobId + "] " + process.pid());
                    System.out.flush();
                    
                    // Reassemble full original command to maintain exact test syntax output (including trailing &)
                    String commandWithAmpersand = String.join(" ", parts) + " &";
                    backgroundJobs.add(new Job(nextJobId, process.pid(), commandWithAmpersand, "Running"));
                    nextJobId++;
                } else {
                    process.waitFor();
                }
            } catch (Exception e) {
                System.out.println(command + ": command not found");
            }
        }
    }
}