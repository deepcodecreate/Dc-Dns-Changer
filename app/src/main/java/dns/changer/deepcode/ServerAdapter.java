package dns.changer.deepcode;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ServerViewHolder> {

    public interface ServerSelectionListener {
        void onServerSelected(DnsServer server);
    }

    public interface ServerLongClickListener {
        void onLongClick(DnsServer server);
    }

    private Context context;
    private List<DnsServer> serverList;
    private ServerSelectionListener selectionListener;
    private ServerLongClickListener longClickListener;
    private SharedPreferences prefs;

    public ServerAdapter(Context context, List<DnsServer> serverList,
                         ServerSelectionListener selectionListener,
                         ServerLongClickListener longClickListener) {
        this.context = context;
        this.serverList = serverList;
        this.selectionListener = selectionListener;
        this.longClickListener = longClickListener;
        this.prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE);
    }

    public void updateServerList(List<DnsServer> newList) {
        this.serverList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ServerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_server, parent, false);
        return new ServerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServerViewHolder holder, int position) {
        DnsServer server = serverList.get(position);
        holder.serverName.setText(server.getName());

        StringBuilder dnsText = new StringBuilder();
        dnsText.append("\nIPv4: ").append(server.getDns1());
        if (!server.getDns2().isEmpty()) {
            dnsText.append(" / ").append(server.getDns2());
        }

        if (!server.getIpv6Dns1().isEmpty()) {
            dnsText.append("\nIPv6: ").append(server.getIpv6Dns1());
            if (!server.getIpv6Dns2().isEmpty()) {
                dnsText.append(" / ").append(server.getIpv6Dns2());
            }
        }

        boolean isEnglish = prefs.getBoolean("english_language", false);
        if (server.isChecking()) {
            dnsText.append("\n").append(isEnglish ? "Ping: Checking..." : "پینگ: در حال بررسی...");
            holder.dnsAddress.setTextColor(Color.parseColor("#EEEEEE"));
        } else if (server.getPing() >= 0) {
            dnsText.append(isEnglish ? "\nPing:" : "\nپینگ:").append(server.getPing()).append("ms");
            holder.dnsAddress.setTextColor(getPingColor(server.getPing()));
        } else {
            dnsText.append("\n").append(isEnglish ? "Ping: Failed" : "پینگ: ناموفق");
            holder.dnsAddress.setTextColor(Color.parseColor("#FF0000"));
        }

        holder.dnsAddress.setText(dnsText.toString());
        holder.itemView.setOnClickListener(v -> selectionListener.onServerSelected(server));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onLongClick(server);
            return true;
        });

        holder.shareIcon.setOnClickListener(v -> {
            String base = "dnschanger://add?";
            StringBuilder link = new StringBuilder(base);
            link.append("name=").append(Uri.encode(server.getName()));
            link.append("&dns1=").append(Uri.encode(server.getDns1()));

            if (!server.getDns2().isEmpty())
                link.append("&dns2=").append(Uri.encode(server.getDns2()));
            if (!server.getIpv6Dns1().isEmpty())
                link.append("&ipv6dns1=").append(Uri.encode(server.getIpv6Dns1()));
            if (!server.getIpv6Dns2().isEmpty())
                link.append("&ipv6dns2=").append(Uri.encode(server.getIpv6Dns2()));

            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("DNS Link", link.toString());
            clipboard.setPrimaryClip(clip);

            String toastText = isEnglish ? "Copied to clipboard" : "کپی شد";
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
        });
    }

    private int getPingColor(int ping) {
        if (ping < 80) return Color.parseColor("#4CAF50");
        if (ping < 120) return Color.parseColor("#FFC107");
        if (ping <= 200) return Color.parseColor("#FF632e");
        return Color.parseColor("#FF0000");
    }

    @Override
    public int getItemCount() {
        return serverList.size();
    }

    static class ServerViewHolder extends RecyclerView.ViewHolder {
        TextView serverName;
        TextView dnsAddress;
        ImageView shareIcon;

        public ServerViewHolder(@NonNull View itemView) {
            super(itemView);
            serverName = itemView.findViewById(R.id.server_name);
            dnsAddress = itemView.findViewById(R.id.dns_address);
            shareIcon = itemView.findViewById(R.id.share_icon);
        }
    }
}