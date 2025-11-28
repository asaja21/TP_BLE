package UPsay.decouverteAndroid;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
// Nouveaux imports pour la publicité GATT
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS_CODE = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser; // <-- NOUVEL ATTRIBUT

    // Attributs pour la liste des appareils
    private ArrayList<String> discoveredDevicesList;
    private ArrayAdapter<String> listAdapter;

    // Attributs pour le scan BLE
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialisation du gestionnaire et de l'adaptateur Bluetooth
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        // Vérifier si le Bluetooth est supporté par l'appareil
        if (bluetoothAdapter == null) {
            Log.e("BluetoothGATT", "Cet appareil ne supporte pas le Bluetooth.");
            Toast.makeText(this, "Cet appareil ne supporte pas le Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialisation et configuration de la ListView
        ListView devicesListView = findViewById(R.id.devices_list);
        discoveredDevicesList = new ArrayList<>();
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, discoveredDevicesList);
        devicesListView.setAdapter(listAdapter);

        // Ajout du listener pour les clics sur les items de la liste
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String deviceInfo = discoveredDevicesList.get(position);
                // Pour l'instant, on affiche juste un Toast avec l'information de l'appareil cliqué.
                // Plus tard, on pourra extraire l'adresse MAC et lancer la connexion.
                Toast.makeText(MainActivity.this, "Appareil sélectionné : " + deviceInfo, Toast.LENGTH_SHORT).show();
                Log.d("BluetoothGATT", "Appareil cliqué : " + deviceInfo);

                // Exemple de ce qu'on fera plus tard :
                // String deviceAddress = deviceInfo.substring(deviceInfo.lastIndexOf("\n") + 1);
                // BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                // connectToDevice(device);
            }
        });

        // Liaison du bouton et définition de son action
        Button connectButton = findViewById(R.id.connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Au lieu d'activer le bluetooth, on lance directement le scan
                // qui s'occupera de vérifier les permissions et l'état du BT.
                scanLeDevice();
            }
        });
    }

    /**
     * Lance le processus d'activation du Bluetooth en vérifiant d'abord les permissions.
     */
    public void activateBluetooth() {
        if (checkAndRequestPermissions()) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                // Le Bluetooth est déjà activé, on lance le scan.
                scanLeDevice();
            }
        }
    }

    /**
     * Lance ou arrête la recherche d'appareils Bluetooth LE.
     */
    private void scanLeDevice() {
        // Vérification des permissions avant de scanner
        if (!checkAndRequestPermissions()) {
            Log.d("BluetoothGATT", "Permissions non accordées. Impossible de scanner.");
            return;
        }

        // Vérification que le Bluetooth est activé
        if (!bluetoothAdapter.isEnabled()) {
            Log.d("BluetoothGATT", "Bluetooth non activé. Demande d'activation.");
            activateBluetooth();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e("BluetoothGATT", "Impossible d'obtenir le BluetoothLeScanner.");
            Toast.makeText(this, "Impossible de scanner les appareils BLE.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!scanning) {
            Log.d("BluetoothGATT", "Lancement du scan BLE...");
            Toast.makeText(this, "Recherche des appareils...", Toast.LENGTH_SHORT).show();
            // Vider la liste avant de commencer un nouveau scan
            discoveredDevicesList.clear();
            listAdapter.notifyDataSetChanged();
            scanning = true;
            // Démarrer le scan
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            Log.d("BluetoothGATT", "Arrêt du scan BLE.");
            scanning = false;
            // Arrêter le scan
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    // Callback pour les résultats du scan BLE
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            String deviceName = device.getName();
            String deviceAddress = device.getAddress();
            String deviceInfo = (deviceName != null ? deviceName : "Appareil inconnu") + "\n" + deviceAddress;

            // Éviter les doublons dans la liste
            if (!discoveredDevicesList.contains(deviceInfo)) {
                Log.d("BluetoothGATT", "Appareil trouvé: " + deviceInfo);
                discoveredDevicesList.add(deviceInfo);
                listAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e("BluetoothGATT", "Le scan BLE a échoué avec le code d'erreur: " + errorCode);
            Toast.makeText(MainActivity.this, "Échec du scan BLE", Toast.LENGTH_SHORT).show();
            scanning = false;
        }
    };

    /**
     * Vérifie les permissions Bluetooth nécessaires et les demande si elles ne sont pas accordées.
     * @return true si les permissions sont déjà accordées, false sinon.
     */
    private boolean checkAndRequestPermissions() {
        // Ajout de BLUETOOTH_SCAN pour la découverte d'appareils
        String[] permissionsToRequest = {
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN
        };

        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissionsToRequest) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSIONS_CODE);
            return false;
        }
        return true;
    }

    /**
     * Gère le résultat de la demande d'activation du Bluetooth.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.d("BluetoothGATT", "Bluetooth activé par l'utilisateur.");
                Toast.makeText(this, "Bluetooth activé.", Toast.LENGTH_SHORT).show();
                // On peut maintenant lancer la suite, par exemple la publicité du serveur GATT.
            } else {
                Log.d("BluetoothGATT", "L'utilisateur a refusé d'activer le Bluetooth.");
                Toast.makeText(this, "L'activation du Bluetooth est nécessaire.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Gère le résultat de la demande de permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Log.d("BluetoothGATT", "Permissions accordées. Tentative d'activation du Bluetooth.");
                // Les permissions ont été accordées, on peut relancer l'activation du Bluetooth.
                activateBluetooth();
            } else {
                Log.d("BluetoothGATT", "L'utilisateur a refusé les permissions Bluetooth.");
                Toast.makeText(this, "Les permissions Bluetooth sont nécessaires pour continuer.", Toast.LENGTH_LONG).show();
            }
        }
    }
}