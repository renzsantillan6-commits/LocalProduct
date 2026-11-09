import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public class LocalProduct extends JFrame {

    // ============================================================
    // Database configuration — adjust if your XAMPP setup differs
    // ============================================================
    private static final String DB_URL =
        "jdbc:mysql://localhost:3306/local_product_db"
        + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";   // default XAMPP has no password

    // ============================================================
    // Product model (mirrors the products table)
    // ============================================================
    static class Product {
        int id;
        String name;
        String category;
        double price;
        int stock;

        Product(int id, String name, String category,
                double price, int stock) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.stock = stock;
        }
    }

    // In-memory cache of products (source of truth is MySQL)
    private final ArrayList<Product> products = new ArrayList<>();
    private int editingId = -1;

    // Input fields
    private final JTextField nameField = new JTextField(15);
    private final JTextField categoryField = new JTextField(15);
    private final JTextField priceField = new JTextField(15);
    private final JTextField stockField = new JTextField(15);
    private final JTextField searchField = new JTextField(20);

    // Dashboard labels
    private final JLabel totalLabel = new JLabel("0");
    private final JLabel stockLabel = new JLabel("0");
    private final JLabel valueLabel = new JLabel("₱0.00");

    // Product table
    private final DefaultTableModel tableModel =
        new DefaultTableModel(
            new String[]{"ID", "Name", "Category", "Price", "Stock"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

    private final JTable productTable = new JTable(tableModel);

    // ============================================================
    // Constructor
    // ============================================================
    public LocalProduct() {
        setTitle("Local Product Management");
        setSize(950, 650);
        setMinimumSize(new Dimension(750, 550));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Fail fast if DB is unreachable
        if (!testConnection()) {
            JOptionPane.showMessageDialog(
                null,
                "Could not connect to MySQL.\n"
                + "Check that XAMPP MySQL is running and the\n"
                + "database 'local_product_db' exists.",
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }

        buildInterface();
        loadProductsFromDb();
        refreshTable();
    }

    // ============================================================
    // DB Helpers
    // ============================================================
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private boolean testConnection() {
        try (Connection c = getConnection()) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Reads all rows from MySQL into the in-memory list. */
    private void loadProductsFromDb() {
        products.clear();
        String sql = "SELECT id, name, category, price, stock "
                   + "FROM products ORDER BY id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                products.add(new Product(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getDouble("price"),
                    rs.getInt("stock")
                ));
            }
        } catch (SQLException e) {
            showDbError("load products", e);
        }
    }

    // ============================================================
    // UI Construction (unchanged layout, same look & feel)
    // ============================================================
    private void buildInterface() {
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(243, 245, 249));

        JLabel header = new JLabel("LOCAL PRODUCT MANAGEMENT");
        header.setOpaque(true);
        header.setBackground(new Color(23, 37, 84));
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Arial", Font.BOLD, 22));
        header.setBorder(
            BorderFactory.createEmptyBorder(20, 22, 20, 22)
        );
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(new Color(243, 245, 249));
        content.setBorder(
            BorderFactory.createEmptyBorder(5, 15, 15, 15)
        );
        add(content, BorderLayout.CENTER);

        // Dashboard
        JPanel topPanel = new JPanel(new BorderLayout(10, 12));
        topPanel.setOpaque(false);
        content.add(topPanel, BorderLayout.NORTH);

        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        statsPanel.setOpaque(false);
        statsPanel.add(createStatCard("TOTAL PRODUCTS", totalLabel));
        statsPanel.add(createStatCard("TOTAL STOCK", stockLabel));
        statsPanel.add(createStatCard("INVENTORY VALUE", valueLabel));
        topPanel.add(statsPanel, BorderLayout.NORTH);

        // Product form
        JPanel formPanel = new JPanel(new BorderLayout(8, 8));
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );

        JLabel formTitle = new JLabel("PRODUCT INFORMATION");
        formTitle.setFont(new Font("Arial", Font.BOLD, 16));
        formPanel.add(formTitle, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(2, 4, 10, 8));
        fields.setOpaque(false);
        fields.add(createFieldPanel("Product Name", nameField));
        fields.add(createFieldPanel("Category", categoryField));
        fields.add(createFieldPanel("Price (₱)", priceField));
        fields.add(createFieldPanel("Stock Quantity", stockField));
        formPanel.add(fields, BorderLayout.CENTER);

        JPanel formButtons = new JPanel(
            new FlowLayout(FlowLayout.LEFT)
        );
        formButtons.setOpaque(false);

        JButton saveButton = createButton(
            "Save Product", new Color(37, 99, 235)
        );
        JButton clearButton = createButton(
            "Clear", new Color(100, 116, 139)
        );
        saveButton.addActionListener(e -> saveProduct());
        clearButton.addActionListener(e -> clearForm());
        formButtons.add(saveButton);
        formButtons.add(clearButton);
        formPanel.add(formButtons, BorderLayout.SOUTH);

        topPanel.add(formPanel, BorderLayout.CENTER);

        // Inventory table
        JPanel inventoryPanel = new JPanel(new BorderLayout(8, 10));
        inventoryPanel.setBackground(Color.WHITE);
        inventoryPanel.setBorder(
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );

        JLabel inventoryTitle = new JLabel("PRODUCT INVENTORY");
        inventoryTitle.setFont(new Font("Arial", Font.BOLD, 16));
        inventoryPanel.add(inventoryTitle, BorderLayout.NORTH);

        JPanel tableArea = new JPanel(new BorderLayout(8, 8));
        tableArea.setOpaque(false);

        JPanel searchPanel = new JPanel(
            new FlowLayout(FlowLayout.LEFT)
        );
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        tableArea.add(searchPanel, BorderLayout.NORTH);

        productTable.setRowHeight(28);
        productTable.setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION
        );

        JScrollPane scrollPane = new JScrollPane(productTable);
        tableArea.add(scrollPane, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(
            new FlowLayout(FlowLayout.RIGHT)
        );
        actionPanel.setOpaque(false);

        JButton editButton = createButton(
            "Edit Selected", new Color(8, 145, 107)
        );
        JButton deleteButton = createButton(
            "Delete Selected", new Color(220, 38, 38)
        );
        editButton.addActionListener(e -> editSelectedProduct());
        deleteButton.addActionListener(e -> deleteSelectedProduct());
        actionPanel.add(editButton);
        actionPanel.add(deleteButton);
        tableArea.add(actionPanel, BorderLayout.SOUTH);

        inventoryPanel.add(tableArea, BorderLayout.CENTER);
        content.add(inventoryPanel, BorderLayout.CENTER);

        // Search listener (filters in memory)
        searchField.getDocument().addDocumentListener(
            new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { refreshTable(); }
                public void removeUpdate(DocumentEvent e) { refreshTable(); }
                public void changedUpdate(DocumentEvent e) { refreshTable(); }
            }
        );
    }

    private JPanel createStatCard(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(5, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 11));

        value.setFont(new Font("Arial", Font.BOLD, 25));
        value.setForeground(new Color(37, 99, 235));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel createFieldPanel(String label, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(3, 4));
        panel.setOpaque(false);
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        return button;
    }

    // ============================================================
    // INSERT / UPDATE
    // ============================================================
    private void saveProduct() {
        String name = nameField.getText().trim();
        String category = categoryField.getText().trim();
        String priceText = priceField.getText().trim();
        String stockText = stockField.getText().trim();

        if (name.isEmpty() || category.isEmpty()
            || priceText.isEmpty() || stockText.isEmpty()) {

            JOptionPane.showMessageDialog(
                this, "Please complete all fields."
            );
            return;
        }

        double price;
        int stock;
        try {
            price = Double.parseDouble(priceText);
            stock = Integer.parseInt(stockText);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(
                this,
                "Enter a valid price and whole-number stock."
            );
            return;
        }

        if (!Double.isFinite(price) || price < 0 || stock < 0) {
            JOptionPane.showMessageDialog(
                this, "Enter valid positive values."
            );
            return;
        }

        boolean isUpdate = (editingId != -1);

        String sql = isUpdate
            ? "UPDATE products SET name=?, category=?, price=?, stock=? "
              + "WHERE id=?"
            : "INSERT INTO products (name, category, price, stock) "
              + "VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, category);
            ps.setDouble(3, price);
            ps.setInt(4, stock);
            if (isUpdate) ps.setInt(5, editingId);

            int affected = ps.executeUpdate();
            if (affected == 0 && isUpdate) {
                JOptionPane.showMessageDialog(
                    this, "Product no longer exists in the database."
                );
                clearForm();
                loadProductsFromDb();
                refreshTable();
                return;
            }

            JOptionPane.showMessageDialog(
                this,
                isUpdate
                    ? "Product updated successfully!"
                    : "Product added successfully!"
            );

            clearForm();
            loadProductsFromDb();
            refreshTable();

        } catch (SQLException e) {
            showDbError(isUpdate ? "update product" : "add product", e);
        }
    }

    // ============================================================
    // Edit (load selected row into form)
    // ============================================================
    private void editSelectedProduct() {
        int row = productTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(
                this, "Please select a product."
            );
            return;
        }

        int id = (int) tableModel.getValueAt(row, 0);
        Product product = findProduct(id);
        if (product == null) return;

        editingId = product.id;
        nameField.setText(product.name);
        categoryField.setText(product.category);
        priceField.setText(String.valueOf(product.price));
        stockField.setText(String.valueOf(product.stock));
    }

    // ============================================================
    // DELETE
    // ============================================================
    private void deleteSelectedProduct() {
        int row = productTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(
                this, "Please select a product."
            );
            return;
        }

        int id = (int) tableModel.getValueAt(row, 0);

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete this product?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        String sql = "DELETE FROM products WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

            if (editingId == id) clearForm();

            loadProductsFromDb();
            refreshTable();

            JOptionPane.showMessageDialog(
                this, "Product deleted successfully!"
            );

        } catch (SQLException e) {
            showDbError("delete product", e);
        }
    }

    // ============================================================
    // Form helpers
    // ============================================================
    private void clearForm() {
        nameField.setText("");
        categoryField.setText("");
        priceField.setText("");
        stockField.setText("");
        editingId = -1;
        productTable.clearSelection();
    }

    private Product findProduct(int id) {
        for (Product product : products) {
            if (product.id == id) return product;
        }
        return null;
    }

    // ============================================================
    // Refresh table + dashboard (filter by search in memory)
    // ============================================================
    private void refreshTable() {
        tableModel.setRowCount(0);

        String search = searchField.getText().trim().toLowerCase();

        int totalStock = 0;
        double totalValue = 0;

        for (Product product : products) {
            totalStock += product.stock;
            totalValue += product.price * product.stock;

            boolean matches =
                search.isEmpty()
                || String.valueOf(product.id).contains(search)
                || product.name.toLowerCase().contains(search)
                || product.category.toLowerCase().contains(search);

            if (matches) {
                tableModel.addRow(new Object[]{
                    product.id,
                    product.name,
                    product.category,
                    String.format("₱%.2f", product.price),
                    product.stock
                });
            }
        }

        totalLabel.setText(String.valueOf(products.size()));
        stockLabel.setText(String.valueOf(totalStock));
        valueLabel.setText(String.format("₱%.2f", totalValue));
    }

    // ============================================================
    // Error helper
    // ============================================================
    private void showDbError(String action, SQLException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(
            this,
            "Database error while trying to " + action + ":\n"
            + e.getMessage(),
            "Database Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    // ============================================================
    // Main
    // ============================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LocalProduct app = new LocalProduct();
            app.setVisible(true);
        });
    }
}
