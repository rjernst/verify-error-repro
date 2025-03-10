import java.lang.invoke.MethodHandles;
import java.time.Duration;

public class TestMain {
    public static void main(String[] args) {
        System.out.println("1 hour is " + Duration.ofHours(1).getSeconds() + " seconds");
    }
}
