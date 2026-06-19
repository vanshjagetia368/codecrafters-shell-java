import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
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
        // Builtin commands supported for tab completion in this stage
        String[] builtins = {"echo", "exit", "type"};

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            StringBuilder inputBuilder = new StringBuilder();

            // Set terminal into raw mode to capture immediate keystrokes like Tab
            String[] setRaw = {"/bin/sh", "-c", "stty -icanon -echo min 1 < /dev/tty"};
            Runtime.getRuntime().exec(setRaw).waitFor();

            InputStream in = System.in;

            while (true) {
                int readByte = in.read();
                if (readByte == -1) {
                    break;
                }

                char c = (char) readByte;

                // Handle Enter key (Newline)
                if (c == '\n' || c == '\r') {
                    // Restore terminal configurations back to standard cooked mode
                    String[] setCooked = {"/bin/sh", "-c", "stty sane < /dev/tty"};
                    Runtime.getRuntime().exec(setCooked).waitFor();
                    System.out.println();
                    break;
                }

                // Handle Tab Autocompletion
                else if (c == '\t') {
                    String currentInput = inputBuilder.toString();
                    String matchedBuiltin = null;

                    for (String builtin : builtins) {
                        if (!currentInput.isEmpty() && builtin.startsWith(currentInput)) {
                            matchedBuiltin = builtin;
                            break;
                        }
                    }

                    if (matchedBuiltin != null) {
                        // complete the remaining slice of the string and append a space
                        String completedText = matchedBuiltin.substring(currentInput.length()) + " ";
                        inputBuilder.append(completedText);
                        
                        System.out.print(completedText);
                        System.out.flush();
                    } else {
                        // Alert tone if no matching prefix is discovered
                        System.out.print("\u0007");
                        System.out.flush();
                    }
                }

                // Handle Backspace (127 is standard Linux/macOS backspace byte)
                else if (readByte == 127 || c == '\b') {
                    if (inputBuilder.length() > 0) {
                        inputBuilder.deleteCharAt(inputBuilder.length() - 1);
                        // Move cursor back, overwrite with space, move cursor back again
                        System.out.print("\b \b");
                        System.out.flush();
                    }
                }

                // Handle standard printable characters
                else {
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

            // Handle Redirection Syntaxes
            for (int i = 0; i < parts.size(); i++) {
                String token = parts.get(i);

                if (token.equals(">") || token.equals("1>")) {
                    if (i + 1 < parts.size()) {
                        stdoutFile = parts.get(i + 1);
                        appendStdout = false;
                    }
                    List<String> newParts = new ArrayList<>(parts.subList(0, i));
                    if (i + 2 < parts.size()) {
                        newParts.addAll(parts.subList(i + 2, parts.size()));
                    }
                    parts = newParts;
                    i--;
                } 
                else if (token.equals(">>") || token.equals("1>>")) {
                    if (i + 1 < parts.size()) {
                        stdoutFile = parts.get(i + 1);
                        appendStdout = true;
                    }
                    List<String> newParts = new ArrayList<>(parts.subList(0, i));
                    if (i + 2 < parts.size()) {
                        newParts.addAll(parts.subList(i + 2, parts.size()));
                    }
                    parts = newParts;
                    i--;
                } 
                else if (token.equals("2>")) {
                    if (i + 1 < parts.size()) {
                        stderrFile = parts.get(i + 1);
                        appendStderr = false;
                    }
                    List<String> newParts = new ArrayList<>(parts.subList(0, i));
                    if (i + 2 < parts.size()) {
                        newParts.addAll(parts.subList(i + 2, parts.size()));
                    }
                    parts = newParts;
                    i--;
                } 
                else if (token.equals("2>>")) {
                    if (i + 1 < parts.size()) {
                        stderrFile = parts.get(i + 1);
                        appendStderr = true;
                    }
                    List<String> newParts = new ArrayList<>(parts.subList(0, i));
                    if (i + 2 < parts.size()) {
                        newParts.addAll(parts.subList(i + 2, parts.size()));
                    }
                    parts = newParts;
                    i--;
                }
            }

            if (parts.isEmpty()) {
                continue;
            }

            String command = parts.get(0);

            // Exit Builtin
            if (command.equals("exit")) {
                break;
            }

            // Echo Builtin
            else if (command.equals("echo")) {
                StringBuilder output = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) {
                        output.append(" ");
                    }
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
                        // Touch/create empty file if stderr target declared
                    }
                }
                continue;
            }

            // Type Builtin
            else if (command.equals("type")) {
                if (parts.size() < 2) {
                    continue;
                }

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
                    System.out.println(result);
                }

                if (stderrFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(stderrFile, appendStderr)) {
                        // Touch file
                    }
                }
                continue;
            }

            // Executable File Commands (PATH Routing)
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
                    if (appendStdout) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(stdoutFile)));
                    } else {
                        pb.redirectOutput(new File(stdoutFile));
                    }
                }

                if (stderrFile != null) {
                    if (appendStderr) {
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(stderrFile)));
                    } else {
                        pb.redirectError(new File(stderrFile));
                    }
                }

                Process process = pb.start();
                process.waitFor();

                if (stdoutFile == null) {
                    process.getInputStream().transferTo(System.out);
                }
                if (stderrFile == null) {
                    process.getErrorStream().transferTo(System.out);
                }

            } catch (Exception e) {
                System.out.println(command + ": command not found");
            }
        }
    }
}