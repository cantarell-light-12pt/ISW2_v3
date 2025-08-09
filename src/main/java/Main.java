import it.uniroma2.dicii.Application;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        boolean verbose = args.length > 0 && args[0].equals("-v");
        Application app = new Application();
        app.execute(verbose);
    }
}
