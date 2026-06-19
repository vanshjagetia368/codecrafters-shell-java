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

    String command = parts.get(0);

    if (command.equals("exit")) {
        break;
    }

    else if (command.equals("echo")) {

    String stderrFile = null;
    int redirectIndex = -1;

    for (int i = 1; i < parts.size(); i++) {
        if (parts.get(i).equals("2>")) {
            redirectIndex = i;

            if (i + 1 < parts.size()) {
                stderrFile = parts.get(i + 1);
            }
            break;
        }
    }

    StringBuilder output = new StringBuilder();

    int limit = redirectIndex == -1 ? parts.size() : redirectIndex;

    for (int i = 1; i < limit; i++) {
        if (i > 1) output.append(" ");
        output.append(parts.get(i));
    }

    System.out.println(output);

    // Create/truncate stderr file even if echo produces no stderr
    if (stderrFile != null) {
        try {
            new java.io.FileOutputStream(stderrFile).close();
        } catch (Exception ignored) {
        }
    }
}

    else if (command.equals("type")) {

        if (parts.size() < 2) {
            continue;
        }

        String cmd = parts.get(1);

        if (cmd.equals("echo")
                || cmd.equals("exit")
                || cmd.equals("type")) {

            System.out.println(cmd + " is a shell builtin");
        } else {

            boolean found = false;

            String[] paths =
                    System.getenv("PATH").split(File.pathSeparator);

            for (String dir : paths) {
                File file = new File(dir, cmd);

                if (file.exists() && file.canExecute()) {
                    System.out.println(
                            cmd + " is " + file.getAbsolutePath());
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println(cmd + ": not found");
            }
        }
    }

    else {

        String stderrFile = null;

        for (int i = 0; i < parts.size(); i++) {

            if (parts.get(i).equals("2>")) {

                if (i + 1 < parts.size()) {
                    stderrFile = parts.get(i + 1);
                }

                parts = new ArrayList<>(parts.subList(0, i));
                break;
            }
        }

        boolean found = false;

        String[] paths =
                System.getenv("PATH").split(File.pathSeparator);

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

            ProcessBuilder pb = new ProcessBuilder(parts);

            if (stderrFile != null) {
                pb.redirectError(new File(stderrFile));
            }

            Process process = pb.start();

            InputStream stdout = process.getInputStream();

            int ch;
            while ((ch = stdout.read()) != -1) {
                System.out.print((char) ch);
            }

            process.waitFor();

        } catch (Exception e) {
            System.out.println(command + ": command not found");
        }
    }
}
       
    }
}
