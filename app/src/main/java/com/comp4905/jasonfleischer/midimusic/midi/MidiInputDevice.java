package com.comp4905.jasonfleischer.midimusic.midi;

import com.comp4905.jasonfleischer.midimusic.MainActivity;
import com.comp4905.jasonfleischer.midimusic.R;
import com.comp4905.jasonfleischer.midimusic.fragments.FragMentManager;
import com.comp4905.jasonfleischer.midimusic.util.HLog;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

/**
 * MIDI Input Device
 * stop() method must be called when the application will be destroyed.
 * 
 * @author K.Shoji
 */
public final class MidiInputDevice {

	//private final UsbDevice usbDevice;
	private final UsbDeviceConnection usbDeviceConnection;
	private final UsbInterface usbInterface;
	private final UsbEndpoint inputEndpoint;

	private final OnMidiInputEventListener midiEventListener;

	private final WaiterThread waiterThread;

	/**
	 * constructor
	 * 
	 * @param usbDevice
	 * @param usbDeviceConnection
	 * @param usbInterface
	 * @param midiEventListener
	 * @throws IllegalArgumentException endpoint not found.
	 */
	public MidiInputDevice(UsbDevice usbDevice, UsbDeviceConnection usbDeviceConnection, UsbInterface usbInterface, UsbEndpoint usbEndpoint, OnMidiInputEventListener midiEventListener) throws IllegalArgumentException {
		//this.usbDevice = usbDevice;
		this.usbDeviceConnection = usbDeviceConnection;
		this.usbInterface = usbInterface;

		this.midiEventListener = midiEventListener;

		waiterThread = new WaiterThread();

		inputEndpoint = usbEndpoint;


		usbDeviceConnection.claimInterface(usbInterface, true);
		waiterThread.setPriority(8);
		waiterThread.setName("MidiInputDevice[" + usbDevice.getDeviceName() + "].WaiterThread");
		waiterThread.start();
		FragMentManager.getInstance().updateUSBConnection(true);
	}

	/**
	 * stops the watching thread
	 */
	public void stop() {
		usbDeviceConnection.releaseInterface(usbInterface);

		waiterThread.stopFlag = true;
		resume();
		FragMentManager.getInstance().updateUSBConnection(false);
		// blocks while the thread will stop
		while (waiterThread.isAlive()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}

	/**
	 * Suspends event listening
	 */
	public void suspend() {
		FragMentManager.getInstance().updateUSBConnection(false);
		synchronized (waiterThread.suspendSignal) {
			waiterThread.suspendFlag = true;
		}
	}

	/**
	 * Resumes event listening
	 */
	public void resume() {
		FragMentManager.getInstance().updateUSBConnection(true);
		synchronized (waiterThread.suspendSignal) {
			waiterThread.suspendFlag = false;
			waiterThread.suspendSignal.notifyAll();
		}
	}

	/**
	 * Polling thread for input data.
	 * Loops infinitely while stopFlag == false.
	 * 
	 * @author K.Shoji
	 */
	private final class WaiterThread extends Thread {
		private volatile boolean stopFlag;
		private final Object suspendSignal = new Object();
		private volatile boolean suspendFlag;

		/**
		 * constructor
		 */
		WaiterThread() {
			stopFlag = false;
			suspendFlag = false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			final UsbDeviceConnection deviceConnection = usbDeviceConnection;
			final UsbEndpoint usbEndpoint = inputEndpoint;
			final MidiInputDevice sender = MidiInputDevice.this;
			final OnMidiInputEventListener eventListener = midiEventListener;
			final int maxPacketSize = inputEndpoint.getMaxPacketSize();
			// prepare buffer variables
			final byte[] bulkReadBuffer = new byte[maxPacketSize];
			byte[] readBuffer = new byte[maxPacketSize * 2]; // *2 for safety (BUFFER_LENGTH+4 would be enough)
			int readBufferSize = 0;
			byte[] read = new byte[maxPacketSize * 2];
			@SuppressWarnings("resource")
			final ReusableByteArrayOutputStream systemExclusive = new ReusableByteArrayOutputStream();
			int length, cable, codeIndexNumber, byte1, byte2, byte3, i;

			// Don't allocate instances in the loop, as much as possible.
			HLog.i(MainActivity.getInstance().getResources().getString(R.string.usb_connected));
			//FragMentManager.getInstance().updateUSBConnection(true);
			while (!stopFlag) {
				length = deviceConnection.bulkTransfer(usbEndpoint, bulkReadBuffer, maxPacketSize, 0);

				synchronized (suspendSignal) {
					if (suspendFlag) {
						try {
							// check the deviceConnection to ignore events while suspending.
							// Note: Events received within last sleeping(100msec) will be sent to the eventListener.
							suspendSignal.wait(100);
						} catch (InterruptedException e) {
							// ignore exception
						}
						continue;
					}
				}

				if (length <= 0) {
					continue;
				}

				System.arraycopy(bulkReadBuffer, 0, readBuffer, readBufferSize, length);
				readBufferSize += length;

				if (readBufferSize < 4) {
					// more data needed
					continue;
				}

				// USB MIDI data stream: 4 bytes boundary
				final int readSize = readBufferSize / 4 * 4;
				System.arraycopy(readBuffer, 0, read, 0, readSize); // fill the read array

				// keep unread bytes
				final int unreadSize = readBufferSize - readSize;
				if (unreadSize > 0) {
					System.arraycopy(readBuffer, readSize, readBuffer, 0, unreadSize);
					readBufferSize = unreadSize;
				} else {
					readBufferSize = 0;
				}

				for (i = 0; i < readSize; i += 4) {
					cable = (read[i + 0] >> 4) & 0xf;
					codeIndexNumber = read[i + 0] & 0xf;
					byte1 = read[i + 1] & 0xff;
					byte2 = read[i + 2] & 0xff;
					byte3 = read[i + 3] & 0xff;

					switch (codeIndexNumber) {
					case 0:
						eventListener.onMidiMiscellaneousFunctionCodes(sender, cable, byte1, byte2, byte3);
						break;
					case 1:
						eventListener.onMidiCableEvents(sender, cable, byte1, byte2, byte3);
						break;
					case 2:
						// system common message with 2 bytes
					{
						byte[] bytes = new byte[] { (byte) byte1, (byte) byte2 };
						eventListener.onMidiSystemCommonMessage(sender, cable, bytes);
					}
					break;
					case 3:
						// system common message with 3 bytes
					{
						byte[] bytes = new byte[] { (byte) byte1, (byte) byte2, (byte) byte3 };
						eventListener.onMidiSystemCommonMessage(sender, cable, bytes);
					}
					break;
					case 4:
						// sysex starts, and has next
						synchronized (systemExclusive) {
							systemExclusive.write(byte1);
							systemExclusive.write(byte2);
							systemExclusive.write(byte3);
						}
						break;
					case 5:
						// system common message with 1byte
						// sysex end with 1 byte
						if (systemExclusive == null) {
							byte[] bytes = new byte[] { (byte) byte1 };
							eventListener.onMidiSystemCommonMessage(sender, cable, bytes);
						} else {
							synchronized (systemExclusive) {
								systemExclusive.write(byte1);
								eventListener.onMidiSystemExclusive(sender, cable, systemExclusive.toByteArray());
								systemExclusive.reset();
							}
						}
						break;
					case 6:
						// sysex end with 2 bytes
						if (systemExclusive != null) {
							synchronized (systemExclusive) {
								systemExclusive.write(byte1);
								systemExclusive.write(byte2);
								eventListener.onMidiSystemExclusive(sender, cable, systemExclusive.toByteArray());
								systemExclusive.reset();
							}
						}
						break;
					case 7:
						// sysex end with 3 bytes
						if (systemExclusive != null) {
							synchronized (systemExclusive) {
								systemExclusive.write(byte1);
								systemExclusive.write(byte2);
								systemExclusive.write(byte3);
								eventListener.onMidiSystemExclusive(sender, cable, systemExclusive.toByteArray());
								systemExclusive.reset();
							}
						}
						break;
					case 8:
						eventListener.onMidiNoteOff(sender, cable, byte1 & 0xf, byte2, byte3);
						break;
					case 9:
						if (byte3 == 0x00) {
							eventListener.onMidiNoteOff(sender, cable, byte1 & 0xf, byte2, byte3);
						} else {
							eventListener.onMidiNoteOn(sender, cable, byte1 & 0xf, byte2, byte3);
						}
						break;
					case 10:
						// poly key press
						eventListener.onMidiPolyphonicAftertouch(sender, cable, byte1 & 0xf, byte2, byte3);
						break;
					case 11:
						// control change
						eventListener.onMidiControlChange(sender, cable, byte1 & 0xf, byte2, byte3);
						processRpnMessages(cable, byte1, byte2, byte3, sender);
						break;
					case 12:
						// program change
						eventListener.onMidiProgramChange(sender, cable, byte1 & 0xf, byte2);
						break;
					case 13:
						// channel pressure
						eventListener.onMidiChannelAftertouch(sender, cable, byte1 & 0xf, byte2);
						break;
					case 14:
						// pitch bend
						eventListener.onMidiPitchWheel(sender, cable, byte1 & 0xf, byte2 | (byte3 << 7));
						break;
					case 15:
						// single byte
						eventListener.onMidiSingleByte(sender, cable, byte1);
						break;
					default:
						// do nothing.
						break;
					}
				}
			}
		}

		private RPNStatus rpnStatus = RPNStatus.NONE;
		private int rpnFunctionMSB = 0x7f;
		private int rpnFunctionLSB = 0x7f;
		private int nrpnFunctionMSB = 0x7f;
		private int nrpnFunctionLSB = 0x7f;
		private int rpnValueMSB;

		/**
		 * RPN and NRPN messages
		 * 
		 * @param cable
		 * @param byte1
		 * @param byte2
		 * @param byte3
		 */
		private void processRpnMessages(int cable, int byte1, int byte2, int byte3, MidiInputDevice sender) {
			switch (byte2) {
			case 6:
				rpnValueMSB = byte3 & 0x7f;
				if (rpnStatus == RPNStatus.RPN) {
					midiEventListener.onMidiRPNReceived(sender, cable, byte1, ((rpnFunctionMSB & 0x7f) << 7) & (rpnFunctionLSB & 0x7f), rpnValueMSB, -1);
				} else if (rpnStatus == RPNStatus.NRPN) {
					midiEventListener.onMidiNRPNReceived(sender, cable, byte1, ((nrpnFunctionMSB & 0x7f) << 7) & (nrpnFunctionLSB & 0x7f), rpnValueMSB, -1);
				}
				break;
			case 38:
				if (rpnStatus == RPNStatus.RPN) {
					midiEventListener.onMidiRPNReceived(sender, cable, byte1, ((rpnFunctionMSB & 0x7f) << 7) & (rpnFunctionLSB & 0x7f), rpnValueMSB, byte3 & 0x7f);
				} else if (rpnStatus == RPNStatus.NRPN) {
					midiEventListener.onMidiNRPNReceived(sender, cable, byte1, ((nrpnFunctionMSB & 0x7f) << 7) & (nrpnFunctionLSB & 0x7f), rpnValueMSB, byte3 & 0x7f);
				}
				break;
			case 98:
				nrpnFunctionLSB = byte3 & 0x7f;
				rpnStatus = RPNStatus.NRPN;
				break;
			case 99:
				nrpnFunctionMSB = byte3 & 0x7f;
				rpnStatus = RPNStatus.NRPN;
				break;
			case 100:
				rpnFunctionLSB = byte3 & 0x7f;
				if (rpnFunctionMSB == 0x7f && rpnFunctionLSB == 0x7f) {
					rpnStatus = RPNStatus.NONE;
				} else {
					rpnStatus = RPNStatus.RPN;
				}
				break;
			case 101:
				rpnFunctionMSB = byte3 & 0x7f;
				if (rpnFunctionMSB == 0x7f && rpnFunctionLSB == 0x7f) {
					rpnStatus = RPNStatus.NONE;
				} else {
					rpnStatus = RPNStatus.RPN;
				}
				break;
			default:
				break;
			}
		}
	}

	/**
	 * current RPN status
	 * 
	 * @author K.Shoji
	 */
	private enum RPNStatus {
		RPN, NRPN, NONE
	}
}
