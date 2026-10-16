package dns.changer.deepcode;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
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

    /** Swaps in a filtered list and animates inserts, removals and moves. */
    public void submitFiltered(List<DnsServer> newList) {
        final List<DnsServer> oldList = this.serverList;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return newList.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                return key(oldList.get(o)).equals(key(newList.get(n)));
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                return oldList.get(o).getPing() == newList.get(n).getPing();
            }
        });
        this.serverList = newList;
        result.dispatchUpdatesTo(this);
    }

    private static String key(DnsServer s) {
        return s.getProtocol() + "|" + s.getName() + "|" + s.getDns1() + "|" + s.getHostname() + "|" + s.getDohUrl();
    }

    @NonNull
    @Override
    public ServerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_server, parent, false);
        return new ServerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServerViewHolder holder, int position) {
        final DnsServer server = serverList.get(position);
        holder.serverName.setText(server.getName());
        if (holder.protocolBadge != null) {
            holder.protocolBadge.setText(server.getProtocolEnum().label);
            ThemeManager.ThemeDef theme = ThemeManager.byId(
                    ThemeManager.getSelectedId(holder.itemView.getContext()));
            GradientDrawable badgeBackground = new GradientDrawable();
            badgeBackground.setShape(GradientDrawable.RECTANGLE);
            badgeBackground.setColor(theme.previewAccent);
            badgeBackground.setCornerRadius(8f * holder.itemView.getResources()
                    .getDisplayMetrics().density);
            holder.protocolBadge.setBackground(badgeBackground);
            holder.protocolBadge.setTextColor(theme.previewOnAccent);
            holder.protocolBadge.setVisibility(View.VISIBLE);
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(server.getEndpointSummary());

        boolean isEnglish = prefs.getBoolean("english_language", false);
        if (server.isChecking()) {
            String checkStr = "\n" + (isEnglish ? "Ping: Checking..." : "پینگ: در حال بررسی...");
            int start = sb.length();
            sb.append(checkStr);
            sb.setSpan(new ForegroundColorSpan(ThemeManager.getDefaultTextColor(holder.itemView.getContext())),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (server.getPing() >= 0) {
            String pingStr = "\n" + (isEnglish ? "Ping: " : "پینگ: ") + server.getPing() + "ms";
            int start = sb.length();
            sb.append(pingStr);
            sb.setSpan(new ForegroundColorSpan(getPingColor(server.getPing())),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            String failStr = "\n" + (isEnglish ? "Ping: Failed" : "پینگ: ناموفق");
            int start = sb.length();
            sb.append(failStr);
            sb.setSpan(new ForegroundColorSpan(Color.parseColor("#FF5252")),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        holder.dnsAddress.setText(sb);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectionListener.onServerSelected(server); }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                longClickListener.onLongClick(server);
                return true;
            }
        });

        holder.shareIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder link = new StringBuilder("dnschanger://add?");
                link.append("name=").append(Uri.encode(server.getName()));
                link.append("&dns1=").append(Uri.encode(server.getDns1()));
                if (!server.getDns2().isEmpty())
                    link.append("&dns2=").append(Uri.encode(server.getDns2()));
                if (!server.getIpv6Dns1().isEmpty())
                    link.append("&ipv6dns1=").append(Uri.encode(server.getIpv6Dns1()));
                if (!server.getIpv6Dns2().isEmpty())
                    link.append("&ipv6dns2=").append(Uri.encode(server.getIpv6Dns2()));
                link.append("&protocol=").append(Uri.encode(server.getProtocol()));
                if (!server.getHostname().isEmpty())
                    link.append("&hostname=").append(Uri.encode(server.getHostname()));
                if (!server.getDohUrl().isEmpty())
                    link.append("&doh=").append(Uri.encode(server.getDohUrl()));
                link.append("&port=").append(server.getPort());

                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("DNS Link", link.toString()));
                Toast.makeText(context, isEnglish ? "Copied to clipboard" : "کپی شد", Toast.LENGTH_SHORT).show();
            }
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
        TextView protocolBadge;
        ImageView shareIcon;

        public ServerViewHolder(@NonNull View itemView) {
            super(itemView);
            serverName = itemView.findViewById(R.id.server_name);
            dnsAddress = itemView.findViewById(R.id.dns_address);
            protocolBadge = itemView.findViewById(R.id.protocol_badge);
            shareIcon = itemView.findViewById(R.id.share_icon);
        }
    }
}
