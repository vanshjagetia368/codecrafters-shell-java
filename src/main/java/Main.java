import java.io.File;
import java.io.FileOutputStream;
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

    Scanner scanner = new Scanner(System.in);

    while (true) {

        System.out.print("$ ");
        System.out.flush();

        if (!scanner.hasNextLine()) {
            break;
        }

        String input = scanner.nextLine();
        List<String> parts = parseCommand(input);

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

                if (i + 1 < parts.size()) {
                    stdoutFile = parts.get(i + 1);
                    appendStdout = false;
                }

                List<String> newParts =
                        new ArrayList<>(parts.subList(0, i));

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

                List<String> newParts =
                        new ArrayList<>(parts.subList(0, i));

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

                List<String> newParts =
                        new ArrayList<>(parts.subList(0, i));

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

                List<String> newParts =
                        new ArrayList<>(parts.subList(0, i));

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

        if (command.equals("exit")) {
            break;
        }

        else if (command.equals("echo")) {

            StringBuilder output = new StringBuilder();

            for (int i = 1; i < parts.size(); i++) {

                if (i > 1) {
                    output.append(" ");
                }

                output.append(parts.get(i));
            }

            String result =
                    output.toString() + System.lineSeparator();

            if (stdoutFile != null) {

                try (FileOutputStream fos =
                             new FileOutputStream(
                                     stdoutFile,
                                     appendStdout)) {

                    fos.write(result.getBytes());
                }

            } else {

                System.out.print(result);
            }

            if (stderrFile != null) {

                try (FileOutputStream fos =
                             new FileOutputStream(
                                     stderrFile,
                                     appendStderr)) {
                }
            }

            continue;
        }

        else if (command.equals("type")) {

            if (parts.size() < 2) {
                continue;
            }

            String cmd = parts.get(1);
            String result;

            if (cmd.equals("echo")
                    || cmd.equals("exit")
                    || cmd.equals("type")) {

                result = cmd + " is a shell builtin";

            } else {

                result = cmd + ": not found";

                String[] paths =
                        System.getenv("PATH")
                                .split(File.pathSeparator);

                for (String dir : paths) {

                    File file = new File(dir, cmd);

                    if (file.exists() && file.canExecute()) {

                        result =
                                cmd + " is "
                                        + file.getAbsolutePath();

                        break;
                    }
                }
            }

            if (stdoutFile != null) {

                try (FileOutputStream fos =
                             new FileOutputStream(
                                     stdoutFile,
                                     appendStdout)) {

                    fos.write(
                            (result + System.lineSeparator())
                                    .getBytes());
                }

            } else {

                System.out.println(result);
            }

            if (stderrFile != null) {

                try (FileOutputStream fos =
                             new FileOutputStream(
                                     stderrFile,
                                     appendStderr)) {
                }
            }

            continue;
        }

        boolean foundExecutable = false;

        String[] paths =
                System.getenv("PATH")
                        .split(File.pathSeparator);

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

            ProcessBuilder pb =
                    new ProcessBuilder(parts);

            if (stdoutFile != null) {

                if (appendStdout) {

                    pb.redirectOutput(
                            ProcessBuilder.Redirect.appendTo(
                                    new File(stdoutFile)));

                } else {

                    pb.redirectOutput(
                            new File(stdoutFile));
                }
            }

            if (stderrFile != null) {

                if (appendStderr) {

                    pb.redirectError(
                            ProcessBuilder.Redirect.appendTo(
                                    new File(stderrFile)));

                } else {

                    pb.redirectError(
                            new File(stderrFile));
                }
            }

            Process process = pb.start();

            if (stdoutFile == null) {

                InputStream stdout =
                        process.getInputStream();

                int ch;

                while ((ch = stdout.read()) != -1) {
                    System.out.print((char) ch);
                }
            }

            process.waitFor();

        } catch (Exception e) {

            System.out.println(
                    command + ": command not found");
        }
    }

    scanner.close();
}

}