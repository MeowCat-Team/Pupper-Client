/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
