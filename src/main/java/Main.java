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

        // Backslash outside quotes
        if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
            if (i + 1 < input.length()) {
                current.append(input.charAt(i + 1));
                i++;
            }
        }
        else if (c == '\'' && !inDoubleQuotes) {
            inSingleQuotes = !inSingleQuotes;
        }
        else if (c == '"' && !inSingleQuotes) {
            inDoubleQuotes = !inDoubleQuotes;
        }
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
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) {
                        System.out.print(" ");
                    }
                    System.out.print(parts.get(i));
                }
                System.out.println();
            }

            else if (command.equals("type")) {
                if (parts.size() < 2) {
                    continue;
                }

                String cmd = parts.get(1);

                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type")) {

                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    String[] paths = pathEnv.split(File.pathSeparator);

                    boolean found = false;

                    for (String dir : paths) {
                        File file = new File(dir, cmd);

                        if (file.exists() && file.canExecute()) {
                            System.out.println(cmd + " is " + file.getAbsolutePath());
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

                if (found) {
                    try {
                        Process process = new ProcessBuilder(parts)
                                .redirectErrorStream(true)
                                .start();

                        InputStream is = process.getInputStream();
                        int ch;

                        while ((ch = is.read()) != -1) {
                            System.out.print((char) ch);
                        }

                        process.waitFor();
                    } catch (Exception e) {
                        System.out.println(command + ": command not found");
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }

        scanner.close();
    }
}
