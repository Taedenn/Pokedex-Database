import com.jcraft.jsch.*;
import java.io.*;
import java.nio.file.*;

public class DatabaseTunnel implements AutoCloseable {

    private static final String DB_HOST = "cs.westminstercollege.edu";
    private static final int DB_SSH_PORT = 2322;                        // ssh port
    private static final String DB_SSH_USER = "student";                // ssh user
    private static final int DB_PORT = 3306;                            // Remote port (MySQL standard port number)

    // ssh fingerprint for database server
    private static final String KNOWN_HOST_ENTRY = "[cs.westminstercollege.edu]:2322 ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDUK8Sdd11vwtHRf0VRFop4g5Mxirk710JtGsphi75TU6o/VJU3gsnHkZjpH0lx4XkRxOkNQ8+87MqkQ6erQac/UKBVRbwTS5CCdp45a7KYRg23by/fjDRwPXzGmP8Er+e5cZbcEQyevFyyAwM9b7OuAdy/XDzu7pOpnLxeBKiaq/EvGrqX2auJkTyZPVh8rfkV3sXXW9Bb/RXbFMb+pPEM4s9qJDibGNQiXJBRjGCedtzZ0AmSyMPxhXn9HLCPHcLN8bHySnr7omDYCtKVBxwOcGoASgUv+czh04dcBUr72Eq6JV7qoe7OVvRdF3qEgx6uH1yTYl9xaUrtqwKWGDZr";
    
    // Private RSA key used for the ssh connection
    private static final String PRIVATE_KEY = "-----BEGIN EC PRIVATE KEY-----\n"
        + "MHcCAQEEIAKECpzSOaxE0IesSj0VSEK4bKTP9bvn7nsALuqw1dmqoAoGCCqGSM49\n"
        + "AwEHoUQDQgAE/SJqcz4fi52Pad9aC4mZSGZ8oWnyba7qiIHr304C25b2FfMZNGJG\n"
        + "s6/k7cTNhbn6L1ggEceH4X9Ojd7uu5OocA==\n"
        + "-----END EC PRIVATE KEY-----\n\n";


    private Session jschSession;
    private int forwardedPort;
    
    public DatabaseTunnel() throws IOException {
        open();
    }
    
    private void open() throws IOException {
        try {
            JSch jsch = new JSch();
            jsch.addIdentity(getKeyfile());
            jsch.setKnownHosts(new ByteArrayInputStream(KNOWN_HOST_ENTRY.getBytes()));
            jschSession = jsch.getSession(DB_SSH_USER, DB_HOST, DB_SSH_PORT);
            jschSession.connect();
            forwardedPort = jschSession.setPortForwardingL(0, "localhost", DB_PORT);
        } catch (JSchException ex) {
            throw new IOException("Unable to open tunnel", ex);
        }
    }
    
    public int getForwardedPort() {
        return forwardedPort;
    }
    
    @Override
    public void close() {
        jschSession.disconnect();
    }
    
    // Creates a file containing the RSA key from PRIVATE_KEY if it doesn't exist. Returns the name of the file.
    private String getKeyfile() throws IOException {
        final String keyfileName = "id_ecdsa.cmpt307.tunnel";
        Path keyfilePath = Path.of(keyfileName);
        if (!Files.exists(keyfilePath))
            Files.writeString(keyfilePath, PRIVATE_KEY, java.nio.charset.StandardCharsets.UTF_8);
        return keyfileName;
    }
}
