package dns.changer.deepcode;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static SpannableString colorizeWords(String input) {
        SpannableString ss = new SpannableString(input);

        Map<Pattern, String> regexColorMap = new HashMap<>();

        
        regexColorMap.put(Pattern.compile("\\b(true|connect|connection|Connection|Updating|connecting)\\b"), "#4CAF50");

        regexColorMap.put(Pattern.compile("\\b(false|log|Error|error|disconnectsd|Disconnected|Stopping|Disconnecting|Read|timed|out)\\b"), "#F44336");

        //regexColorMap.put(Pattern.compile("\\b(ICMP|Logo|Loading|Activity|opening|created|Select|Opening|Initializing|Received)\\b"), "#FFC107");

        regexColorMap.put(Pattern.compile("\\b(DNS|VPN|TCP|DoH|HTTPS|port)\\b"), "#03A9F4");

        //regexColorMap.put(Pattern.compile("\\b(Checking|Setting|Starting|Ping|ping|clicked|resumed|custom|Root|Showing|Status|mode)\\b"), "#FF9800");
        
        regexColorMap.put(Pattern.compile("\\b\\d+\\b"), "#FF9800");

        regexColorMap.put(Pattern.compile("[\\.,:\\-]"), "#FF9800");

        regexColorMap.put(Pattern.compile("\\["), "#FF9800");
        
        regexColorMap.put(Pattern.compile("\\]"), "#FF9800");

        for (Map.Entry<Pattern, String> entry : regexColorMap.entrySet()) {
            applyRegexColor(ss, entry.getKey(), entry.getValue());
        }

        return ss;
    }

    private static void applyRegexColor(SpannableString ss, Pattern pattern, String colorHex) {
        Matcher matcher = pattern.matcher(ss.toString());
        while (matcher.find()) {
            ss.setSpan(new ForegroundColorSpan(Color.parseColor(colorHex)),
                    matcher.start(), matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}