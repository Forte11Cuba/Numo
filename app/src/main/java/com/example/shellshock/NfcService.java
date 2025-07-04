package com.example.shellshock;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

public class NfcService extends HostApduService {

    private static final String TAG = "NfcService";

    // AID for your Satocash application (replace with your actual AID)
    // Example: F000000001 (replace with your actual AID)
    private static final byte[] SATOCASH_AID = hexStringToByteArray("F000000001");

    // Status words for successful execution
    private static final byte[] SW_NO_ERROR = {(byte) 0x90, (byte) 0x00};

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        Log.d(TAG, "Received APDU: " + byteArrayToHexString(apdu));

        // Check if the APDU is a SELECT AID command for our application
        if (apdu.length >= 7 && apdu[0] == (byte) 0x00 && apdu[1] == (byte) 0xA4 && apdu[2] == (byte) 0x04 && apdu[3] == (byte) 0x00) {
            // This is a SELECT command
            int aidLength = apdu[4];
            if (apdu.length >= 5 + aidLength) {
                byte[] receivedAid = Arrays.copyOfRange(apdu, 5, 5 + aidLength);
                if (Arrays.equals(SATOCASH_AID, receivedAid)) {
                    Log.d(TAG, "AID selected: " + byteArrayToHexString(receivedAid));
                    // Respond with success for AID selection
                    return SW_NO_ERROR;
                }
            }
        }

        // --- Placeholder for Satocash client logic ---
        // This is where you would translate the APDU processing logic from satocash_client.py
        // You'll need to parse the APDU command (CLA, INS, P1, P2, Lc, Data, Le)
        // and generate an appropriate response based on the command.

        // Example: If the command is a "GET BALANCE" command (hypothetical INS = 0x01)
        // You would need to define your command structure and handle it here.
        // For demonstration, let's just echo back a simple response for any other command.
        if (apdu.length > 0) {
            byte cla = apdu[0];
            byte ins = apdu[1];
            // ... parse P1, P2, Lc, Data, Le

            Log.d(TAG, String.format("Processing command: CLA=0x%02X, INS=0x%02X", cla, ins));

            // In a real Satocash implementation, you would:
            // 1. Parse the incoming APDU command (e.g., GET BALANCE, DEBIT, CREDIT).
            // 2. Perform the requested operation (e.g., retrieve balance, update balance).
            // 3. Construct the response APDU (data + status words).

            // For now, let's just return a generic success with some dummy data
            // Replace this with your actual Satocash response logic
            byte[] dummyResponseData = "Hello from Satocash!".getBytes();
            byte[] response = new byte[dummyResponseData.length + SW_NO_ERROR.length];
            System.arraycopy(dummyResponseData, 0, response, 0, dummyResponseData.length);
            System.arraycopy(SW_NO_ERROR, 0, response, dummyResponseData.length, SW_NO_ERROR.length);
            return response;
        }

        // If no specific command is recognized, return an error status word
        return new byte[]{(byte) 0x6A, (byte) 0x82}; // SW_FILE_NOT_FOUND or similar error
    }

    @Override
    public void onDeactivated(int reason) {
        Log.d(TAG, "Deactivated: " + reason);
    }

    /**
     * Helper method to convert a byte array to a hexadecimal string.
     */
    public static String byteArrayToHexString(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Helper method to convert a hexadecimal string to a byte array.
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
