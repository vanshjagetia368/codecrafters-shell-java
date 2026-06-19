import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {
    
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
        String[] builtins = {"echo", "exit", "type"};

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            StringBuilder inputBuilder = new StringBuilder();
            int consecutiveTabs = 0;

            // Set terminal into raw mode safely
            String[] setRaw = {"stty", "-icanon", "-echo", "min", "1", "-F", "/dev/tty"};
            Runtime.getRuntime().exec(setRaw).waitFor();

            InputStream in = System.in;

            while (true) {
                int readByte = in.read();
                if (readByte == -1) {
                    break;
                }

                char c = (char) readByte;

                // Handle Enter Key (Newline)
                if (c == '\n' || c == '\r') {
                    consecutiveTabs = 0;
                    String[] setCooked = {"stty", "sane", "-F", "/dev/tty"};
                    Runtime.getRuntime().exec(setCooked).waitFor();
                    System.out.println();
                    break;
                }

                // Handle Tab Autocompletion
                else if (c == '\t') {
                    consecutiveTabs++;
                    String currentInput = inputBuilder.toString();
                    
                    if (!currentInput.isEmpty()) {
                        Set<String> candidatesSet = new LinkedHashSet<>();
                        boolean isArgumentCompletion = currentInput.contains(" ");
                        String partialToken = "";
                        String matchPrefix = "";
                        String targetDirPath = "."; // Fallback to current directory context

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
                            // Check builtins
                            for (String builtin : builtins) {
                                if (builtin.startsWith(matchPrefix)) {
                                    candidatesSet.add(builtin);
                                }
                            }
                            
                            // Check PATH
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
                            String suffix = " "; // Default trailing spacing boundary
                            
                            if (isArgumentCompletion) {
                                // Assess whether the single resolved target is a directory structure 
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
                                    System.out.println(); 
                                    System.out.println(String.join("  ", candidates));
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

                // Handle Backspace
                else if (readByte == 127 || c == '\b') {
                    consecutiveTabs = 0;
                    if (inputBuilder.length() > 0) {
                        inputBuilder.deleteCharAt(inputBuilder.length() - 1);
                        System.out.print("\b \b");
                        System.out.flush();
                    }
                }

                // Handle Regular Characters
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

            String stdoutFile = null;
            String stderrFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;

            // Handle Redirections
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

                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")) {
                    result = cmd + " is a shell builtin";
                } else {
                    result = cmd + ": not found";
                    String[] paths = System.getenv("PATH").split(File.pathSeparator);
                    for (String dir : paths) {
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

            // Route External Processes
            boolean foundExecutable = false;
            String[] paths = System.getenv("PATH").split(File.pathSeparator);
            for (String dir : paths) {
                File file = new File(dir, command);
                if (file.exists() && file.canExecute()) {
                    foundExecutable = true;
                    break;
                }
            }

            if (!foundExecutable) {
                System.out.println(command + ": command not found");
                continue;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(parts);
                if (stdoutFile != null) {
                    pb.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(new File(stdoutFile)) : ProcessBuilder.Redirect.to(new File(stdoutFile)));
                }
                if (stderrFile != null) {
                    pb.redirectError(appendStderr ? ProcessBuilder.Redirect.appendTo(new File(stderrFile)) : ProcessBuilder.Redirect.to(new File(stderrFile)));
                }

                Process process = pb.start();
                process.waitFor();

                if (stdoutFile == null) process.getInputStream().transferTo(System.out);
                if (stderrFile == null) process.getErrorStream().transferTo(System.out);
            } catch (Exception e) {
                System.out.println(command + ": command not found");
            }
        }
    }
}