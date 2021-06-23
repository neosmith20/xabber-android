package com.xabber.android.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.database.realmobjects.GroupMemberRealmObject;
import com.xabber.android.data.extension.avatar.AvatarManager;
import com.xabber.android.data.message.chat.GroupChat;
import com.xabber.android.utils.StringUtils;

import java.util.ArrayList;

public class GroupchatMembersAdapter extends RecyclerView.Adapter<GroupchatMembersAdapter.GroupchatMemberViewHolder>
        implements View.OnClickListener {

    private ArrayList<GroupMemberRealmObject> groupMembers;
    private final GroupChat chat;
    private final OnMemberClickListener listener;
    private RecyclerView recyclerView;

    public GroupchatMembersAdapter(ArrayList<GroupMemberRealmObject> groupMembers, GroupChat chat,
                                   OnMemberClickListener listener) {
        this.groupMembers = groupMembers;
        this.chat = chat;
        this.listener = listener;
    }

    public void setItems(ArrayList<GroupMemberRealmObject> groupMembers) {
        this.groupMembers = groupMembers;
        notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        listener.onMemberClick(groupMembers.get(recyclerView.getChildAdapterPosition(v)));
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @NonNull
    @Override
    public GroupchatMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new GroupchatMemberViewHolder(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.item_groupchat_member, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull GroupchatMemberViewHolder holder, int position) {
        GroupMemberRealmObject bindMember = groupMembers.get(position);

        holder.root.setOnClickListener(this);

        if (bindMember.getNickname() != null && !bindMember.getNickname().isEmpty()) {
            holder.memberName.setText(bindMember.getNickname());
        } else if (bindMember.getJid() != null) {
            holder.memberName.setText(bindMember.getJid());
        }

        if (bindMember.getBadge() != null && !bindMember.getBadge().isEmpty()) {
            holder.memberBadge.setVisibility(View.VISIBLE);
            if (bindMember.isMe())
                holder.memberBadge.setText(bindMember.getBadge() + " " + Application.getInstance().getString(R.string.groupchat_this_is_you));
            else holder.memberBadge.setText(bindMember.getBadge());
        } else {
            if (bindMember.isMe())
                holder.memberBadge.setText(Application.getInstance().getString(R.string.groupchat_this_is_you));
            else holder.memberBadge.setVisibility(View.GONE);
        }

        if (bindMember.getRole() != null && !bindMember.getBadge().isEmpty()) {
            holder.memberRole.setVisibility(View.VISIBLE);
            if (bindMember.getRole().equals("owner")) {
                holder.memberRole.setImageResource(R.drawable.ic_star_filled);
            } else if (bindMember.getRole().equals("admin")) {
                holder.memberRole.setImageResource(R.drawable.ic_star_outline);
            } else {
                holder.memberRole.setVisibility(View.GONE);
            }
        } else {
            holder.memberRole.setVisibility(View.GONE);
        }

        String memberStatus = StringUtils.getLastPresentString(bindMember.getLastSeen());
        holder.memberStatus.setText(memberStatus);
        if (memberStatus.equals(Application.getInstance().getString(R.string.account_state_connected)))
            holder.memberStatus.setTextColor(Application.getInstance().getResources().getColor(R.color.green_800));
        else holder.memberStatus.setTextColor(Application.getInstance().getResources().getColor(R.color.grey_500));

        holder.avatar.setImageDrawable(AvatarManager.getInstance().getGroupMemberAvatar(bindMember, chat.getAccount()));
    }

    @Override
    public int getItemCount() {
        return groupMembers.size();
    }

    static class GroupchatMemberViewHolder extends RecyclerView.ViewHolder {

        private final View root;
        private final ImageView avatar;
        private final ImageView memberRole;
        private final TextView memberName;
        private final TextView memberStatus;
        private final TextView memberBadge;

        public GroupchatMemberViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.member_list_item_root);
            avatar = itemView.findViewById(R.id.ivMemberAvatar);
            memberRole = itemView.findViewById(R.id.ivMemberRole);
            memberName = itemView.findViewById(R.id.tvMemberName);
            memberStatus = itemView.findViewById(R.id.tvMemberStatus);
            memberBadge = itemView.findViewById(R.id.tvMemberBadge);
        }
    }

    public interface OnMemberClickListener{
        void onMemberClick(GroupMemberRealmObject groupMember);
    }

}
