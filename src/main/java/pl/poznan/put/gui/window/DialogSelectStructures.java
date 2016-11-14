package pl.poznan.put.gui.window;

import pl.poznan.put.pdb.analysis.PdbModel;
import pl.poznan.put.structure.tertiary.StructureManager;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DialogSelectStructures extends JDialog {
    public static final int CANCEL = 0;
    public static final int OK = 1;
    private static final Dimension INITIAL_STRUCTURE_LIST_SIZE =
            new Dimension(320, 420);

    private final DefaultListModel<PdbModel> modelAll =
            new DefaultListModel<>();
    private final DefaultListModel<PdbModel> modelSelected =
            new DefaultListModel<>();
    private final JList<PdbModel> listAll = new JList<>(modelAll);
    private final JList<PdbModel> listSelected = new JList<>(modelSelected);
    private final JScrollPane scrollPaneAll = new JScrollPane(listAll);
    private final JScrollPane scrollPaneSelected =
            new JScrollPane(listSelected);

    private final List<PdbModel> selectedStructures = new ArrayList<>();
    private final ListCellRenderer<? super PdbModel> renderer =
            listAll.getCellRenderer();
    private final ListCellRenderer<PdbModel> pdbCellRenderer =
            new ListCellRenderer<PdbModel>() {
                @Override
                public Component getListCellRendererComponent(
                        JList<? extends PdbModel> list, PdbModel value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) renderer
                            .getListCellRendererComponent(list, value, index,
                                                          isSelected,
                                                          cellHasFocus);
                    if (value != null) {
                        label.setText(StructureManager.getName(value));
                    }
                    assert label != null;
                    return label;
                }
            };
    private final JButton buttonSelect = new JButton("Select ->");
    private final JButton buttonSelectAll = new JButton("Select all ->");
    private final JButton buttonDeselect = new JButton("<- Deselect");
    private final JButton buttonDeselectAll = new JButton("<- Deselect all");
    private final JButton buttonOk = new JButton("OK");
    private final JButton buttonCancel = new JButton("Cancel");

    private int chosenOption;

    public DialogSelectStructures(Frame owner) {
        super(owner, "MCQ4Structures: structure selection", true);

        listAll.setBorder(
                BorderFactory.createTitledBorder("Available structures"));
        listAll.setCellRenderer(pdbCellRenderer);
        listSelected.setBorder(
                BorderFactory.createTitledBorder("Selected structures"));
        listSelected.setCellRenderer(pdbCellRenderer);
        scrollPaneAll.setPreferredSize(
                DialogSelectStructures.INITIAL_STRUCTURE_LIST_SIZE);
        scrollPaneSelected.setPreferredSize(
                DialogSelectStructures.INITIAL_STRUCTURE_LIST_SIZE);

        buttonSelect.setEnabled(false);
        buttonDeselect.setEnabled(false);

        JPanel panelButtons = new JPanel();
        panelButtons.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panelButtons.add(buttonSelect, constraints);
        constraints.gridy++;
        panelButtons.add(buttonSelectAll, constraints);
        constraints.gridy++;
        panelButtons.add(buttonDeselect, constraints);
        constraints.gridy++;
        panelButtons.add(buttonDeselectAll, constraints);

        JPanel panelMain = new JPanel();
        panelMain.setLayout(new GridBagLayout());
        constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.VERTICAL;
        panelMain.add(scrollPaneAll, constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.VERTICAL;
        panelMain.add(panelButtons, constraints);
        constraints.gridx = 2;
        constraints.fill = GridBagConstraints.VERTICAL;
        panelMain.add(scrollPaneSelected, constraints);

        JPanel panelOkCancel = new JPanel();
        panelOkCancel.add(buttonOk);
        panelOkCancel.add(buttonCancel);

        setLayout(new BorderLayout());
        add(panelMain, BorderLayout.CENTER);
        add(panelOkCancel, BorderLayout.SOUTH);
        pack();

        Dimension size = getSize();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = screenSize.width - size.width;
        int y = screenSize.height - size.height;
        setLocation(x / 2, y / 2);

        ListSelectionListener listSelectionListener =
                new ListSelectionListener() {
                    @Override
                    public void valueChanged(ListSelectionEvent arg0) {
                        assert arg0 != null;
                        ListSelectionModel source =
                                (ListSelectionModel) arg0.getSource();
                        if (source.equals(listAll.getSelectionModel())) {
                            buttonSelect
                                    .setEnabled(!listAll.isSelectionEmpty());
                        } else if (source
                                .equals(listSelected.getSelectionModel())) {
                            buttonDeselect.setEnabled(
                                    !listSelected.isSelectionEmpty());
                        }
                    }
                };
        listAll.getSelectionModel()
               .addListSelectionListener(listSelectionListener);
        listSelected.getSelectionModel()
                    .addListSelectionListener(listSelectionListener);

        ActionListener actionListenerSelectDeselect = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                List<PdbModel> values = Collections.emptyList();
                boolean isSelect = false;

                assert arg0 != null;
                Object source = arg0.getSource();
                if (source.equals(buttonSelect)) {
                    values = listAll.getSelectedValuesList();
                    isSelect = true;
                } else if (source.equals(buttonSelectAll)) {
                    values = Collections.list(modelAll.elements());
                    isSelect = true;
                } else if (source.equals(buttonDeselect)) {
                    values = listSelected.getSelectedValuesList();
                    isSelect = false;
                } else if (source.equals(buttonDeselectAll)) {
                    values = Collections.list(modelSelected.elements());
                    isSelect = false;
                }

                for (PdbModel f : values) {
                    if (isSelect) {
                        modelAll.removeElement(f);
                        modelSelected.addElement(f);
                    } else {
                        modelAll.addElement(f);
                        modelSelected.removeElement(f);
                    }
                }

                buttonOk.setEnabled(modelSelected.size() > 1);
            }
        };
        buttonSelect.addActionListener(actionListenerSelectDeselect);
        buttonSelectAll.addActionListener(actionListenerSelectDeselect);
        buttonDeselect.addActionListener(actionListenerSelectDeselect);
        buttonDeselectAll.addActionListener(actionListenerSelectDeselect);

        buttonOk.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedStructures.clear();
                selectedStructures
                        .addAll(Collections.list(modelSelected.elements()));
                chosenOption = DialogSelectStructures.OK;
                dispose();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chosenOption = DialogSelectStructures.CANCEL;
                dispose();
            }
        });
    }

    public List<PdbModel> getStructures() {
        return Collections.unmodifiableList(selectedStructures);
    }

    public int showDialog() {
        List<PdbModel> setManager = StructureManager.getAllStructures();
        ArrayList<PdbModel> listLeft = Collections.list(modelAll.elements());
        ArrayList<PdbModel> listRight =
                Collections.list(modelSelected.elements());

        ArrayList<PdbModel> list = new ArrayList<>(listLeft);
        list.removeAll(setManager);
        for (PdbModel structure : list) {
            modelAll.removeElement(structure);
        }

        list = new ArrayList<>(listRight);
        list.removeAll(setManager);
        for (PdbModel structure : list) {
            modelSelected.removeElement(structure);
        }

        setManager.removeAll(listLeft);
        setManager.removeAll(listRight);
        for (PdbModel file : setManager) {
            modelAll.addElement(file);
        }

        buttonOk.setEnabled(modelSelected.size() > 1);
        chosenOption = DialogSelectStructures.CANCEL;
        setVisible(true);
        return chosenOption;
    }
}
