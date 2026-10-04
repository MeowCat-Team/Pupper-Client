package cn.pupperclient.management.hypixel.api;

public class HypixelUser {

	private final String uuid;
	private final String networkLevel, bedwarsLevel;
	private final String winLoseRatio, finalKillDeathRatio, bedsBrokeLostRatio;
	
	public HypixelUser(String uuid, String networkLevel, String bedwarsLevel, String winLoseRatio,
			String finalKillDeathRatio, String bedsBrokeLostRatio) {
		this.uuid = uuid;
		this.networkLevel = networkLevel;
		this.bedwarsLevel = bedwarsLevel;
		this.winLoseRatio = winLoseRatio;
		this.finalKillDeathRatio = finalKillDeathRatio;
		this.bedsBrokeLostRatio = bedsBrokeLostRatio;
	}

	public String getUuid() {
		return uuid;
	}

	public String getNetworkLevel() {
		return networkLevel;
	}

	public String getBedwarsLevel() {
		return bedwarsLevel;
	}

	public String getWinLoseRatio() {
		return winLoseRatio;
	}

	public String getFinalKillDeathRatio() {
		return finalKillDeathRatio;
	}

	public String getBedsBrokeLostRatio() {
		return bedsBrokeLostRatio;
	}
}
