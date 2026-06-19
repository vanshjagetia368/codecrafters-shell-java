import java.io.File;
import java.io.InputStream;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
       Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = scanner.nextLine();

            // exit builtin
            if (input.equals("exit")) {
                break;
            }

            // echo builtin
            else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }

            // type builtin
            else if (input.startsWith("type ")) {
                String command = input.substring(5);

                if (command.equals("echo")
                        || command.equals("exit")
                        || command.equals("type")) {

                    System.out.println(command + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    String[] paths = pathEnv.split(File.pathSeparator);

                    boolean found = false;

                    for (String dir : paths) {
                        File file = new File(dir, command);

                        if (file.exists() && file.canExecute()) {
                            System.out.println(command + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        System.out.println(command + ": not found");
                    }
                }
            }

            // external programs
            else {
                String[] parts = input.split(" ");
                String command = parts[0];

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
