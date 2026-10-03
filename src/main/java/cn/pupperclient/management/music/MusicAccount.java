package cn.pupperclient.management.music;

/** An immutable NetEase session shared by the player and login clients. */
public record MusicAccount(String cookie, String userId, String nickname, String phone) {
    public static final MusicAccount GUEST = new MusicAccount("", "", "", "");
    public MusicAccount {
        cookie = cookie == null ? "" : cookie;
        userId = userId == null ? "" : userId;
        nickname = nickname == null ? "" : nickname;
        phone = phone == null ? "" : phone;
    }
    public boolean authenticated() { return !cookie.isBlank() && !userId.isBlank(); }
    public String owner() { return authenticated() ? "netease:" + userId : "guest"; }
    @Override public String toString() { return "MusicAccount[authenticated=" + authenticated() + "]"; }
}
