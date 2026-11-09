import java.sql.Connection;
import java.sql.DriverManager;

public class DbTest {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/local_product_db"
                   + "?useSSL=false&serverTimezone=UTC";
        try (Connection conn =
                 DriverManager.getConnection(url, "root", "")) {
            System.out.println("Connected!");
        }
    }
}
