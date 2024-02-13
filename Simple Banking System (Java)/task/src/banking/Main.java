package banking;

import java.sql.*;
import java.util.Random;
import java.util.Scanner;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static String dbURL = "jdbc:sqlite:";
    private static Connection conn = null;

    public static void main(String[] args) {
        parseArguments(args);
        setupDatabase();
        runMenu();
        closeDatabase();
    }

    private static void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("-fileName".equals(args[i])) {
                dbURL += args[i + 1];
                break;
            }
        }
    }

    private static void setupDatabase() {
        try {
            conn = DriverManager.getConnection(dbURL);
            if (conn != null) {
                try (Statement stmt = conn.createStatement()) {
                    String sql = "CREATE TABLE IF NOT EXISTS card ("
                            + "id INTEGER PRIMARY KEY,"
                            + "number TEXT,"
                            + "pin TEXT,"
                            + "balance INTEGER DEFAULT 0)";
                    stmt.executeUpdate(sql);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void closeDatabase() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void runMenu() {
        while (true) {
            System.out.println("1. Create an account\n2. Log into account\n0. Exit");
            int choice = scanner.nextInt();
            switch (choice) {
                case 1:
                    createAccount();
                    break;
                case 2:
                    logIntoAccount();
                    break;
                case 0:
                    System.out.println("Bye!");
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private static void createAccount() {
        String cardNumber = generateCardNumber();
        String pin = String.format("%04d", new Random().nextInt(10000));

        try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO card (number, pin) VALUES (?, ?)")) {
            pstmt.setString(1, cardNumber);
            pstmt.setString(2, pin);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Your card has been created");
        System.out.println("Your card number:");
        System.out.println(cardNumber);
        System.out.println("Your card PIN:");
        System.out.println(pin);
    }

    private static void logIntoAccount() {
        System.out.println("Enter your card number:");
        String cardNumber = scanner.next();
        System.out.println("Enter your PIN:");
        String pin = scanner.next();

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT balance FROM card WHERE number = ? AND pin = ?")) {
            pstmt.setString(1, cardNumber);
            pstmt.setString(2, pin);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("You have successfully logged in!");
                    manageAccount(cardNumber);
                } else {
                    System.out.println("Wrong card number or PIN!");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void manageAccount(String cardNumber) {
        while (true) {
            System.out.println("1. Balance\n2. Add income\n3. Do transfer\n4. Close account\n5. Log out\n0. Exit");
            int choice = scanner.nextInt();
            switch (choice) {
                case 1:
                    checkBalance(cardNumber);
                    break;
                case 2:
                    addIncome(cardNumber);
                    break;
                case 3:
                    doTransfer(cardNumber);
                    break;
                case 4:
                    closeAccount(cardNumber);
                    return; // Return to the main menu after closing the account
                case 5:
                    System.out.println("You have successfully logged out!");
                    return;
                case 0:
                    System.out.println("Bye!");
                    System.exit(0);
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private static void checkBalance(String cardNumber) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT balance FROM card WHERE number = ?")) {
            pstmt.setString(1, cardNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Balance: " + rs.getInt("balance"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void addIncome(String cardNumber) {
        System.out.println("Enter income:");
        int income = scanner.nextInt();

        try (PreparedStatement pstmt = conn.prepareStatement("UPDATE card SET balance = balance + ? WHERE number = ?")) {
            pstmt.setInt(1, income);
            pstmt.setString(2, cardNumber);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Income was added!");
    }

    private static void doTransfer(String cardNumber) {
        System.out.println("Transfer\nEnter card number:");
        String receiverCardNumber = scanner.next();

        if (!isCardNumberValid(receiverCardNumber)) {
            System.out.println("Probably you made a mistake in the card number. Please try again!");
            return;
        }

        if (cardNumber.equals(receiverCardNumber)) {
            System.out.println("You can't transfer money to the same account!");
            return;
        }

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM card WHERE number = ?")) {
            pstmt.setString(1, receiverCardNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Such a card does not exist.");
                    return;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Enter how much money you want to transfer:");
        int transferAmount = scanner.nextInt();

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT balance FROM card WHERE number = ?")) {
            pstmt.setString(1, cardNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int currentBalance = rs.getInt("balance");
                    if (transferAmount > currentBalance) {
                        System.out.println("Not enough money!");
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // Proceed with transfer
        try (PreparedStatement pstmtReduce = conn.prepareStatement("UPDATE card SET balance = balance - ? WHERE number = ?")) {
            pstmtReduce.setInt(1, transferAmount);
            pstmtReduce.setString(2, cardNumber);
            pstmtReduce.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        try (PreparedStatement pstmtAdd = conn.prepareStatement("UPDATE card SET balance = balance + ? WHERE number = ?")) {
            pstmtAdd.setInt(1, transferAmount);
            pstmtAdd.setString(2, receiverCardNumber);
            pstmtAdd.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Success!");
    }

    private static void closeAccount(String cardNumber) {
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM card WHERE number = ?")) {
            pstmt.setString(1, cardNumber);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("The account has been closed!");
    }

    private static String generateCardNumber() {
        String bin = "400000";
        String accountIdentifier = String.format("%09d", new Random().nextInt(1000000000));
        String cardNumberWithoutChecksum = bin + accountIdentifier;
        int checksum = calculateLuhnChecksum(cardNumberWithoutChecksum);
        return cardNumberWithoutChecksum + checksum;
    }

    private static int calculateLuhnChecksum(String cardNumberWithoutChecksum) {
        int sum = 0;
        for (int i = 0; i < cardNumberWithoutChecksum.length(); i++) {
            int digit = Character.getNumericValue(cardNumberWithoutChecksum.charAt(i));
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static boolean isCardNumberValid(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }

        int checksum = Character.getNumericValue(cardNumber.charAt(cardNumber.length() - 1));
        String cardNumberWithoutChecksum = cardNumber.substring(0, cardNumber.length() - 1);

        return calculateLuhnChecksum(cardNumberWithoutChecksum) == checksum;
    }
}
