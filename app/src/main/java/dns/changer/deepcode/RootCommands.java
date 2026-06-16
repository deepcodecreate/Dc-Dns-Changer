package dns.changer.deepcode;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;

public class RootCommands {

    public static boolean changeDns(String dns1, String dns2, String ipv6Dns1, String ipv6Dns2) {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            if (dns1 != null && !dns1.isEmpty()) {
                outputStream.writeBytes("setprop net.dns1 " + dns1 + "\n");
                outputStream.writeBytes("setprop net.rmnet0.dns1 " + dns1 + "\n");
                outputStream.writeBytes("setprop net.ppp0.dns1 " + dns1 + "\n");
            }
            if (dns2 != null && !dns2.isEmpty()) {
                outputStream.writeBytes("setprop net.dns2 " + dns2 + "\n");
                outputStream.writeBytes("setprop net.rmnet0.dns2 " + dns2 + "\n");
                outputStream.writeBytes("setprop net.ppp0.dns2 " + dns2 + "\n");
            }

            if (ipv6Dns1 != null && !ipv6Dns1.isEmpty()) {
                outputStream.writeBytes("setprop net.dns3 " + ipv6Dns1 + "\n");
            }
            if (ipv6Dns2 != null && !ipv6Dns2.isEmpty()) {
                outputStream.writeBytes("setprop net.dns4 " + ipv6Dns2 + "\n");
            }

            outputStream.writeBytes("exit\n");
            outputStream.flush();
            su.waitFor();

            return su.exitValue() == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean testRootAccess() {
    try {
        Process process = Runtime.getRuntime().exec("su");
        OutputStream outputStream = process.getOutputStream();
        outputStream.write("echo 'Testing root access'\n".getBytes());
        outputStream.write("exit\n".getBytes());
        outputStream.flush();
        outputStream.close();
        int exitValue = process.waitFor();
        return exitValue == 0;
    } catch (Exception e) {
        return false;
    }
  }

    public static boolean restoreOriginalDns() {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            outputStream.writeBytes("setprop net.dns1 \"\"\n");
            outputStream.writeBytes("setprop net.dns2 \"\"\n");
            outputStream.writeBytes("setprop net.dns3 \"\"\n");
            outputStream.writeBytes("setprop net.dns4 \"\"\n");
            outputStream.writeBytes("setprop net.rmnet0.dns1 \"\"\n");
            outputStream.writeBytes("setprop net.rmnet0.dns2 \"\"\n");
            outputStream.writeBytes("setprop net.ppp0.dns1 \"\"\n");
            outputStream.writeBytes("setprop net.ppp0.dns2 \"\"\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            su.waitFor();

            return su.exitValue() == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isDnsChanged(String savedDns1) {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(su.getInputStream()));

            outputStream.writeBytes("getprop net.dns1\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();

            String currentDns = inputStream.readLine();
            su.waitFor();

            return currentDns != null && currentDns.equals(savedDns1);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}