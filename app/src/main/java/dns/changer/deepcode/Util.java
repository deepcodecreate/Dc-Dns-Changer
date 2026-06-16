package dns.changer.deepcode;

public class Util {

    public static boolean isRootAvailable() {
        return checkSuExists();
    }

    private static boolean checkSuExists() {
        String[] paths = {
            "/system/bin/",
            "/system/xbin/",
            "/sbin/",
            "/system/sd/xbin/",
            "/system/bin/failsafe/",
            "/data/local/xbin/",
            "/data/local/bin/",
            "/data/local/"
        };

        for (String path : paths) {
            java.io.File suFile = new java.io.File(path + "su");
            if (suFile.exists()) {
                return true;
            }
        }

        return false;
    }
}