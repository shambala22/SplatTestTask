import org.apache.commons.validator.routines.UrlValidator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ForkJoinPool;

/**
 * Created by shambala on 09.08.17.
 */
public class LogSearch {
    public static final String TITLE = "Log search";
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private FileSystemView fileSystemView;
    private JPanel gui;
    private JPanel fileView;
    private JTextArea fileText;
    private JButton selectPath;
    private JFileChooser chooser;
    private JTextField pathField;
    private JTextField extensionField;

    private File searchPath = new File("/");

    private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final UrlValidator urlValidator = new UrlValidator();

    private Container getGUI() {
        gui = new JPanel(new BorderLayout(3,3));
        gui.setBorder(new EmptyBorder(5,5,5,5));
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new GridBagLayout());
        JTextField searchField = new JTextField();

        gui.add(topPanel, BorderLayout.NORTH);
        fileSystemView = FileSystemView.getFileSystemView();
        fileView = new JPanel(new BorderLayout(3,3));
        fileText = new JTextArea();
        fileText.setWrapStyleWord(true);
        fileText.setLineWrap(true);
        fileText.setEditable(false);
        fileView.add(new JScrollPane(fileText));
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(root);
        searchField.addActionListener(actionEvent -> {
            if (new File(pathField.getText()).exists()) {
                searchPath = new File(pathField.getText());
            } else if (urlValidator.isValid(pathField.getText())) {
                try {
                    searchPath = new File(new URI(pathField.getText()));
                } catch (URISyntaxException e) {
                    JOptionPane.showMessageDialog(gui, "Path doesn't exist");
                }
            } else {
                JOptionPane.showMessageDialog(gui, "Path doesn't exist");
                return;
            }
            if (searchPath.isDirectory()) {
                searchFiles(root, searchField.getText(), new File(pathField.getText()), extensionField.getText());
                searchField.setText("");
            } else {
                JOptionPane.showMessageDialog(gui, "Path is not a directory");
            }
        });
        pathField = new JTextField("/");

        selectPath = new JButton("Select path");
        selectPath.addActionListener(event -> {
            chooser = new JFileChooser();
            chooser.setCurrentDirectory(searchPath);
            chooser.setDialogTitle("Choose directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            //
            if (chooser.showOpenDialog(gui) == JFileChooser.APPROVE_OPTION) {
                searchPath = chooser.getSelectedFile();
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });


        GridBagConstraints left = new GridBagConstraints();
        left.anchor = GridBagConstraints.EAST;
        GridBagConstraints right = new GridBagConstraints();
        right.weightx = 2.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.gridwidth = GridBagConstraints.REMAINDER;
        topPanel.add(new JLabel("Search "), left);
        //searchField.setBorder(new LineBorder(Color.black));
        topPanel.add(searchField, right);
        extensionField = new JTextField("log");
        topPanel.add(new JLabel("Extension "), left);
        topPanel.add(extensionField, right);
        topPanel.add(selectPath, left);
        //pathField.setBorder(new LineBorder(Color.black));
        topPanel.add(pathField, right);

        fileTree = new JTree(treeModel);
        fileTree.setRootVisible(false);
        fileTree.addTreeSelectionListener(treeSelectionEvent -> {
            DefaultMutableTreeNode node =
                    (DefaultMutableTreeNode)treeSelectionEvent.getPath().getLastPathComponent();
            loadFile((File)node.getUserObject());
        });
        fileTree.setCellRenderer(new FileTreeCellRenderer());

        JScrollPane treeScroll = new JScrollPane(fileTree);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                treeScroll,
                fileView);
        splitPane.setDividerLocation(200);
        gui.add(splitPane, BorderLayout.CENTER);
        return gui;
    }


    private void searchFiles(final DefaultMutableTreeNode root, String searchRequest, File path, final String extension) {
        fileTree.setEnabled(false);
        SwingWorker<Void, DefaultMutableTreeNode> worker = new SwingWorker<Void, DefaultMutableTreeNode>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (File fileRoot : fileSystemView.getFiles(path, true)) {
                    forkJoinPool.invoke(new SearchTask(fileRoot, searchRequest, extension, fileSystemView)).ifPresent(this::publish);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<DefaultMutableTreeNode> chunks) {
                for (DefaultMutableTreeNode child : chunks) {
                    root.add(child);
                }
            }

            @Override
            protected void done() {
                treeModel.reload(root);
                fileTree.setEnabled(true);
            }
        };
        worker.execute();
    }

    private void loadFile(File file) {
        if (!file.isDirectory()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                fileText.read(reader, "Text");
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileText.setCaretPosition(0);
        }
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            LogSearch search = new LogSearch();
            frame.setContentPane(search.getGUI());

            frame.pack();
            frame.setSize(new Dimension(600, 600));
            frame.setVisible(true);
        });
    }


    class FileTreeCellRenderer extends DefaultTreeCellRenderer {

        private FileSystemView fileSystemView;

        private JLabel label;

        FileTreeCellRenderer() {
            label = new JLabel();
            label.setOpaque(true);
            fileSystemView = FileSystemView.getFileSystemView();
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {

            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            File file = (File)node.getUserObject();
            label.setIcon(fileSystemView.getSystemIcon(file));
            label.setText(fileSystemView.getSystemDisplayName(file));
            if (selected) {
                label.setBackground(backgroundSelectionColor);
                label.setForeground(textSelectionColor);
            } else {
                label.setBackground(backgroundNonSelectionColor);
                label.setForeground(textNonSelectionColor);
            }

            return label;
        }
    }
}
