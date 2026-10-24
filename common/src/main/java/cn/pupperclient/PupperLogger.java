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

package cn.pupperclient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PupperLogger {

	private static final Logger logger = LogManager.getLogger("Pupper Client");

	public static void info(String prefix, String message) {
		logger.info("[Pupper Client/INFO] [" + prefix + "] " + message);
	}

	public static void warn(String prefix, String message) {
		logger.warn("[Pupper Client/WARN] [" + prefix + "] " + message);
	}

	public static void error(String prefix, String message) {
		logger.error("[Pupper Client/ERROR] [" + prefix + "] " + message);
	}

	public static void error(String prefix, String message, Exception e) {
		logger.error("[Pupper Client/ERROR] [" + prefix + "] " + message, e);
	}

	public static Logger getLogger() {
		return logger;
	}
}
