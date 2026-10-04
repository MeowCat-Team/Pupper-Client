/*
 * Created on Jun 28, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package cn.pupperclient.libraries.flac;

import java.util.HashSet;
import java.util.Iterator;

import cn.pupperclient.libraries.flac.frame.Frame;
import cn.pupperclient.libraries.flac.metadata.Metadata;

/**
 * Class to handle frame listeners.
 * 
 * @author kc7bfi
 */
class FrameListeners implements FrameListener {
	private HashSet<FrameListener> frameListeners = new HashSet<FrameListener>();

	/**
	 * Add a frame listener.
	 * 
	 * @param listener The frame listener to add
	 */
	public void addFrameListener(FrameListener listener) {
		synchronized (frameListeners) {
			frameListeners.add(listener);
		}
	}

	/**
	 * Remove a frame listener.
	 * 
	 * @param listener The frame listener to remove
	 */
	public void removeFrameListener(FrameListener listener) {
		synchronized (frameListeners) {
			frameListeners.remove(listener);
		}
	}

	/**
	 * Process metadata records.
	 * 
	 * @param metadata the metadata block
	 * @see FrameListener#processMetadata(cn.pupperclient.libraries.flac.metadata.Metadata)
	 */
	public void processMetadata(Metadata metadata) {
		synchronized (frameListeners) {
			Iterator<FrameListener> it = frameListeners.iterator();
			while (it.hasNext()) {
				FrameListener listener = (FrameListener) it.next();
				listener.processMetadata(metadata);
			}
		}
	}

	/**
	 * Process data frames.
	 * 
	 * @param frame the data frame
	 * @see FrameListener#processFrame(cn.pupperclient.libraries.flac.frame.Frame)
	 */
	public void processFrame(Frame frame) {
		synchronized (frameListeners) {
			Iterator<FrameListener> it = frameListeners.iterator();
			while (it.hasNext()) {
				FrameListener listener = (FrameListener) it.next();
				listener.processFrame(frame);
			}
		}
	}

	/**
	 * Called for each frame error detected.
	 * 
	 * @param msg The error message
	 * @see FrameListener#processError(java.lang.String)
	 */
	public void processError(String msg) {
		synchronized (frameListeners) {
			Iterator<FrameListener> it = frameListeners.iterator();
			while (it.hasNext()) {
				FrameListener listener = (FrameListener) it.next();
				listener.processError(msg);
			}
		}
	}

}
