
The following command produces a VerifyError on Java 24:

```
java -Dagent.retransform=true src/TestHarness.java
```

However, if running without retransforming, the VerifyError does not occur:

```
java -Dagent.retransform=false src/TestHarness.java
```
