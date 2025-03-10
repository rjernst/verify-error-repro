package checker;

public interface TestChecker {
    void check(Integer i);

    static TestChecker instance() {
        return TestCheckerImpl.instance;
    }

    class TestCheckerImpl implements TestChecker {
        private static final TestCheckerImpl instance = new TestCheckerImpl();

        @Override
        public void check(Integer i) {
            System.out.println("Checking: " + i);
        }
    }
}
