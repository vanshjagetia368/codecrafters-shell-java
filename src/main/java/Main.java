import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
public class Main {
    private static List<String> parseCommand(String input) {
    List<String> args = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    boolean inSingleQuotes = false;
    boolean inDoubleQuotes = false;

    for (int i = 0; i < input.length(); i++) {
        char c = input.charAt(i);

        // Backslash inside double quotes
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
        }

        // Backslash outside quotes
        else if (!inSingleQuotes && !inDoubleQuotes && c == '\\') {
            if (i + 1 < input.length()) {
                current.append(input.charAt(i + 1));
                i++;
            }
        }

        // Single quotes
        else if (c == '\'' && !inDoubleQuotes) {
            inSingleQuotes = !inSingleQuotes;
        }

        // Double quotes
        else if (c == '"' && !inSingleQuotes) {
            inDoubleQuotes = !inDoubleQuotes;
        }

        // Argument separator
        else if (Character.isWhitespace(c)
                && !inSingleQuotes
                && !inDoubleQuotes) {

            if (current.length() > 0) {
                args.add(current.toString());
                current.setLength(0);
            }
        }

        else {
            current.append(c);
        }
    }

    if (current.length() > 0) {
        args.add(current.toString());
    }

    return args;
}
        public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner scanner = new Scanner(System.in);

    while (true) {
        System.out.print("$ ");
        System.out.flush();

        String input = scanner.nextLine();

        List<String> parts = parseCommand(input);

        if (parts.isEmpty()) {
            continue;
        }

        String redirectFile = null;
        List<String> cmdParts = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            String token = parts.get(i);

            if (token.equals(">") || token.equals("1>")) {
                if (i + 1 < parts.size()) {
                    redirectFile = parts.get(i + 1);
                }
                break;
            }

            cmdParts.add(token);
        }

        if (cmdParts.isEmpty()) {
            continue;
        }

        String command = cmdParts.get(0);

        if (command.equals("exit")) {
            break;
        }

        else if (command.equals("echo")) {

            StringBuilder output = new StringBuilder();

            for (int i = 1; i < cmdParts.size(); i++) {
                if (i > 1) {
                    output.append(" ");
                }
                output.append(cmdParts.get(i));
            }

            if (redirectFile != null) {
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(redirectFile),
                        output.toString() + System.lineSeparator()
                );
            } else {
                System.out.println(output);
            }
        }

        else if (command.equals("type")) {

            if (cmdParts.size() < 2) {
                continue;
            }

            String cmd = cmdParts.get(1);

            String result;

            if (cmd.equals("echo") ||
                cmd.equals("exit") ||
                cmd.equals("type")) {

                result = cmd + " is a shell builtin";
            } else {

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                result = cmd + ": not found";

                for (String dir : paths) {
                    File file = new File(dir, cmd);

                    if (file.exists() && file.canExecute()) {
                        result = cmd + " is " + file.getAbsolutePath();
                        break;
                    }
                }
            }

            if (redirectFile != null) {
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(redirectFile),
                        result + System.lineSeparator()
                );
            } else {
                System.out.println(result);
            }
        }

        else {

            String pathEnv = System.getenv("PATH");
            String[] paths = pathEnv.split(File.pathSeparator);

            boolean found = false;

            for (String dir : paths) {
                File file = new File(dir, command);

                if (file.exists() && file.canExecute()) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println(command + ": command not found");
                continue;
            }

            try {

                ProcessBuilder pb = new ProcessBuilder(cmdParts);

                if (redirectFile != null) {
                    pb.redirectOutput(new File(redirectFile));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                }

                pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                Process process = pb.start();

                if (redirectFile == null) {
                    InputStream is = process.getInputStream();

                    int ch;
                    while ((ch = is.read()) != -1) {
                        System.out.print((char) ch);
                    }
                }

                process.waitFor();

            } catch (Exception e) {
                System.out.println(command + ": command not found");
            }
        }
    }

    scanner.close();
       
    }
}
