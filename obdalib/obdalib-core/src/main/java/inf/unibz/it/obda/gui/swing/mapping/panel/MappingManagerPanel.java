/***
 * Copyright (c) 2008, Mariano Rodriguez-Muro. All rights reserved.
 *
 * The OBDA-API is licensed under the terms of the Lesser General Public License
 * v.3 (see OBDAAPI_LICENSE.txt for details). The components of this work
 * include:
 *
 * a) The OBDA-API developed by the author and licensed under the LGPL; and, b)
 * third-party components licensed under terms that may be different from those
 * of the LGPL. Information about such licenses can be found in the file named
 * OBDAAPI_3DPARTY-LICENSES.txt.
 */
package inf.unibz.it.obda.gui.swing.mapping.panel;

import inf.unibz.it.obda.api.controller.APIController;
import inf.unibz.it.obda.api.controller.DatasourcesController;
import inf.unibz.it.obda.api.controller.MappingController;
import inf.unibz.it.obda.api.controller.exception.DuplicateMappingException;
import inf.unibz.it.obda.domain.DataSource;
import inf.unibz.it.obda.domain.OBDAMappingAxiom;
import inf.unibz.it.obda.domain.Query;
import inf.unibz.it.obda.gui.IconLoader;
import inf.unibz.it.obda.gui.swing.MappingValidationDialog;
import inf.unibz.it.obda.gui.swing.datasource.DatasourceSelectorListener;
import inf.unibz.it.obda.gui.swing.datasource.panels.SQLQueryPanel;
import inf.unibz.it.obda.gui.swing.mapping.tree.MappingBodyNode;
import inf.unibz.it.obda.gui.swing.mapping.tree.MappingHeadNode;
import inf.unibz.it.obda.gui.swing.mapping.tree.MappingNode;
import inf.unibz.it.obda.gui.swing.mapping.tree.MappingTreeModel;
import inf.unibz.it.obda.gui.swing.mapping.tree.MappingTreeSelectionModel;
import inf.unibz.it.obda.gui.swing.preferences.OBDAPreferences;
import inf.unibz.it.obda.gui.swing.preferences.OBDAPreferences.MappingManagerPreferenceChangeListener;
import inf.unibz.it.obda.gui.swing.preferences.OBDAPreferences.MappingManagerPreferences;
import inf.unibz.it.obda.gui.swing.treemodel.filter.MappingFunctorTreeModelFilter;
import inf.unibz.it.obda.gui.swing.treemodel.filter.MappingHeadVariableTreeModelFilter;
import inf.unibz.it.obda.gui.swing.treemodel.filter.MappingIDTreeModelFilter;
import inf.unibz.it.obda.gui.swing.treemodel.filter.MappingPredicateTreeModelFilter;
import inf.unibz.it.obda.gui.swing.treemodel.filter.MappingSQLStringTreeModelFilter;
import inf.unibz.it.obda.gui.swing.treemodel.filter.MappingStringTreeModelFilter;
import inf.unibz.it.obda.gui.swing.treemodel.filter.TreeModelFilter;
import inf.unibz.it.obda.rdbmsgav.domain.RDBMSOBDAMappingAxiom;
import inf.unibz.it.obda.rdbmsgav.domain.RDBMSSQLQuery;
import inf.unibz.it.obda.rdbmsgav.validator.RDBMSMappingValidator;
import inf.unibz.it.obda.rdbmsgav.validator.SQLQueryValidator;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.antlr.runtime.RecognitionException;
import org.obda.query.domain.CQIE;
import org.obda.query.tools.parser.DatalogProgramParser;
import org.obda.query.tools.parser.DatalogQueryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mariano
 */
public class MappingManagerPanel extends JPanel implements
    MappingManagerPreferenceChangeListener, DatasourceSelectorListener {

  private final String ID = "id";
  private final String FUNCT = "funct";
  private final String PRED = "pred";
  private final String HEAD = "head";
  private final String SQL = "sql";
  private final String TEXT = "text";

  private DefaultMutableTreeNode editedNode;
  private MappingManagerPreferences pref;
  private KeyStroke addMapping;
  private KeyStroke editBody;
  private KeyStroke editHead;
  private KeyStroke editID;

  private Thread validatorThread;

  private SQLQueryValidator validator;

  private MappingController	mapc;

  private DatasourcesController	dsc;

  protected APIController	apic;

  private DatalogProgramParser datalogParser;

  private DataSource selectedSource;

  private boolean canceled;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Creates a new panel.
   * 
   * @param apic
   *          The API controller object.
   * @param preference
   *          The preference object.
   */
  public MappingManagerPanel(APIController apic, MappingController mapc,
      DatasourcesController dsc, OBDAPreferences preference) {
    
    this.apic = apic;
    this.mapc = mapc;
    this.dsc = dsc;
    pref = preference.getMappingsPreference();
    datalogParser = new DatalogProgramParser();

    initComponents();
    registerAction();
    addMenu();

    /***********************************************************************
     * Setting up the mappings tree
     */
    MappingTreeModel maptreemodel = new MappingTreeModel(apic, mapc);
    mapc.addMappingControllerListener(maptreemodel);
    treMappingsTree.setRootVisible(false);
    treMappingsTree.setModel(maptreemodel);
    MappingRenderer map_renderer = new MappingRenderer(apic, preference);
    treMappingsTree.setCellRenderer(map_renderer);
    treMappingsTree.setEditable(true);
    treMappingsTree.setCellEditor(new MappingTreeNodeCellEditor(treMappingsTree, this, apic));
    treMappingsTree.setSelectionModel(new MappingTreeSelectionModel());
    treMappingsTree.setRowHeight(0);
    treMappingsTree.setMaximumSize(new Dimension(scrMappingsTree.getWidth() - 50, 65000));
    treMappingsTree.setToggleClickCount(1);
    treMappingsTree.setInvokesStopCellEditing(true);
    cmdAddMapping.setIcon(IconLoader.getImageIcon("images/plus.png"));
    cmdAddMapping.setToolTipText("Add a new mapping");
    cmdRemoveMapping.setIcon(IconLoader.getImageIcon("images/minus.png"));
    cmdRemoveMapping.setToolTipText("Remove selected mappings");
    cmdDuplicateMapping.setIcon(IconLoader.getImageIcon("images/duplicate.png"));
    cmdDuplicateMapping.setToolTipText("Duplicate selected mappings");
    pref.registerPreferenceChangedListener(this);
  }

  private void registerAction() {
    
    InputMap inputmap = treMappingsTree.getInputMap();
    ActionMap actionmap = treMappingsTree.getActionMap();

    String add = pref.getShortCut(MappingManagerPreferences.ADD_MAPPING);
    addMapping = KeyStroke.getKeyStroke(add);
    String body = pref.getShortCut(MappingManagerPreferences.EDIT_BODY);
    editBody = KeyStroke.getKeyStroke(body);
    String head = pref.getShortCut(MappingManagerPreferences.EDIT_HEAD);
    editHead = KeyStroke.getKeyStroke(head);
    String id = pref.getShortCut(MappingManagerPreferences.EDIT_ID);
    editID = KeyStroke.getKeyStroke(id);

    AbstractAction addAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        addMapping();
      }
    };
    inputmap.put(addMapping, MappingManagerPreferences.ADD_MAPPING);
    actionmap.put(MappingManagerPreferences.ADD_MAPPING, addAction);

    AbstractAction editBodyAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        TreePath path = treMappingsTree.getSelectionPath();
        if (path == null) {
          return;
        }
        startEditBodyOfMapping(path);
      }
    };
    inputmap.put(editBody, MappingManagerPreferences.EDIT_BODY);
    actionmap.put(MappingManagerPreferences.EDIT_BODY, editBodyAction);

    AbstractAction editHeadAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        TreePath path = treMappingsTree.getSelectionPath();
        if (path == null) {
          return;
        }
        startEditHeadOfMapping(path);
      }
    };
    inputmap.put(editHead, MappingManagerPreferences.EDIT_HEAD);
    actionmap.put(MappingManagerPreferences.EDIT_HEAD, editHeadAction);

    AbstractAction editIDAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        TreePath path = treMappingsTree.getSelectionPath();
        if (path == null) {
          return;
        }
        treMappingsTree.setEditable(true);
        editedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        treMappingsTree.startEditingAtPath(path);
      }
    };
    inputmap.put(editID, MappingManagerPreferences.EDIT_ID);
    actionmap.put(MappingManagerPreferences.EDIT_ID, editIDAction);
  }

  private void addMenu() {
    JMenuItem add = new JMenuItem();
    add.setText("Add Mapping");
    add.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addMapping();
      }
    });
    add.setMnemonic(addMapping.getKeyCode());
    add.setAccelerator(addMapping);
    menuMappings.add(add);

    JMenuItem delete = new JMenuItem();
    delete.setText("Remove Mapping");
    delete.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        removeMapping();
      }
    });
    menuMappings.add(delete);
    menuMappings.addSeparator();

    JMenuItem editID = new JMenuItem();
    editID.setText("Edit Mapping ID");
    editID.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TreePath path = treMappingsTree.getSelectionPath();
        if (path == null) {
          return;
        }
        treMappingsTree.setEditable(true);
        editedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        treMappingsTree.startEditingAtPath(path);
      }
    });
    editID.setMnemonic(this.editID.getKeyCode());
    editID.setAccelerator(this.editID);
    menuMappings.add(editID);

    JMenuItem editHead = new JMenuItem();
    editHead.setText("Edit Mapping Head");
    editHead.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TreePath path = treMappingsTree.getSelectionPath();
        if (path == null) {
          return;
        }
        startEditHeadOfMapping(path);
      }
    });
    editHead.setMnemonic(this.editHead.getKeyCode());
    editHead.setAccelerator(this.editHead);
    menuMappings.add(editHead);

    JMenuItem editBody = new JMenuItem();
    editBody.setText("Edit Mapping Body");
    editBody.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TreePath path = treMappingsTree.getSelectionPath();
        if (path == null) {
          return;
        }
        startEditBodyOfMapping(path);
      }
    });
    editBody.setMnemonic(this.editBody.getKeyCode());
    editBody.setAccelerator(this.editBody);
    menuMappings.add(editBody);
    menuMappings.addSeparator();

    menuValidateAll.setText("Validate");
    menuValidateAll.setEnabled(false);
    menuValidateAll.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuValidateAllActionPerformed(evt);
      }
    });
    menuMappings.add(menuValidateAll);

    menuValidateBody.setText("Validate body");
    menuValidateBody.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuValidateBodyActionPerformed(evt);
      }
    });
    menuMappings.add(menuValidateBody);

    menuValidateHead.setText("Validate head");
    menuValidateHead.setEnabled(false);
    menuValidateHead.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuValidateHeadActionPerformed(evt);
      }
    });
    menuMappings.add(menuValidateHead);
    menuMappings.addSeparator();
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    menuMappings = new javax.swing.JPopupMenu();
    menuValidateAll = new javax.swing.JMenuItem();
    menuValidateBody = new javax.swing.JMenuItem();
    menuValidateHead = new javax.swing.JMenuItem();
    menuExecuteQuery = new javax.swing.JMenuItem();
    scrMappingsManager = new javax.swing.JScrollPane();
    pnlMappingManager = new javax.swing.JPanel();
    pnlMappingButtons = new javax.swing.JPanel();
    txtFilter = new javax.swing.JTextField();
    chkFilter = new javax.swing.JCheckBox();
    cmdAddMapping = new javax.swing.JButton();
    cmdRemoveMapping = new javax.swing.JButton();
    cmdDuplicateMapping = new javax.swing.JButton();
    scrMappingsTree = new javax.swing.JScrollPane();
    treMappingsTree = new javax.swing.JTree();

    menuValidateAll.setText("Validate");
    menuValidateAll.setEnabled(false);
    menuValidateAll.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuValidateAllActionPerformed(evt);
      }
    });
    menuMappings.add(menuValidateAll);

    menuValidateBody.setText("Validate body");
    menuValidateBody.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuValidateBodyActionPerformed(evt);
      }
    });
    menuMappings.add(menuValidateBody);

    menuValidateHead.setText("Validate head");
    menuValidateHead.setEnabled(false);
    menuValidateHead.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuValidateHeadActionPerformed(evt);
      }
    });
    menuMappings.add(menuValidateHead);

    menuExecuteQuery.setText("Execute Query");
    menuExecuteQuery.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        menuExecuteQueryActionPerformed(evt);
      }
    });
    menuMappings.add(menuExecuteQuery);

    setLayout(new java.awt.BorderLayout());

    scrMappingsManager.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

    pnlMappingManager.setAutoscrolls(true);
    pnlMappingManager.setPreferredSize(new java.awt.Dimension(400, 200));
    pnlMappingManager.setLayout(new java.awt.GridBagLayout());

    pnlMappingButtons.setEnabled(false);
    pnlMappingButtons.setLayout(new java.awt.GridBagLayout());

    txtFilter.setPreferredSize(new java.awt.Dimension(250, 20));
    txtFilter.addKeyListener(new java.awt.event.KeyAdapter() {
      @Override
      public void keyPressed(java.awt.event.KeyEvent evt) {
        try {
          sendFilters(evt);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.9;
    pnlMappingButtons.add(txtFilter, gridBagConstraints);

    chkFilter.setText("Apply Filters");
    chkFilter.addItemListener(new java.awt.event.ItemListener() {
      @Override
      public void itemStateChanged(java.awt.event.ItemEvent evt) {
        try {
          chkFilterItemStateChanged(evt);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    pnlMappingButtons.add(chkFilter, new java.awt.GridBagConstraints());

    cmdAddMapping.setIcon(IconLoader.getImageIcon("images/plus.png"));
    cmdAddMapping.setToolTipText("Add new mapping");
    cmdAddMapping.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    cmdAddMapping.setContentAreaFilled(false);
    cmdAddMapping.setIconTextGap(0);
    cmdAddMapping.setMaximumSize(new java.awt.Dimension(25, 25));
    cmdAddMapping.setMinimumSize(new java.awt.Dimension(25, 25));
    cmdAddMapping.setPreferredSize(new java.awt.Dimension(25, 25));
    cmdAddMapping.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cmdAddMappingActionPerformed(evt);
      }
    });
    pnlMappingButtons.add(cmdAddMapping, new java.awt.GridBagConstraints());

    cmdRemoveMapping.setIcon(IconLoader.getImageIcon("images/minus.png"));
    cmdRemoveMapping.setToolTipText("Remove mappings");
    cmdRemoveMapping.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    cmdRemoveMapping.setContentAreaFilled(false);
    cmdRemoveMapping.setIconTextGap(0);
    cmdRemoveMapping.setMaximumSize(new java.awt.Dimension(25, 25));
    cmdRemoveMapping.setMinimumSize(new java.awt.Dimension(25, 25));
    cmdRemoveMapping.setPreferredSize(new java.awt.Dimension(25, 25));
    cmdRemoveMapping.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cmdRemoveMappingActionPerformed(evt);
      }
    });
    pnlMappingButtons.add(cmdRemoveMapping, new java.awt.GridBagConstraints());

    cmdDuplicateMapping.setIcon(IconLoader.getImageIcon("images/plus.png"));
    cmdDuplicateMapping.setToolTipText("Duplicate mappings");
    cmdDuplicateMapping.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    cmdDuplicateMapping.setContentAreaFilled(false);
    cmdDuplicateMapping.setIconTextGap(0);
    cmdDuplicateMapping.setMaximumSize(new java.awt.Dimension(25, 25));
    cmdDuplicateMapping.setMinimumSize(new java.awt.Dimension(25, 25));
    cmdDuplicateMapping.setPreferredSize(new java.awt.Dimension(25, 25));
    cmdDuplicateMapping.addActionListener(new java.awt.event.ActionListener() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cmdDuplicateMappingActionPerformed(evt);
      }
    });
    pnlMappingButtons.add(cmdDuplicateMapping, new java.awt.GridBagConstraints());

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    pnlMappingManager.add(pnlMappingButtons, gridBagConstraints);

    treMappingsTree.setComponentPopupMenu(menuMappings);
    treMappingsTree.setEditable(true);
    scrMappingsTree.setViewportView(treMappingsTree);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 3);
    pnlMappingManager.add(scrMappingsTree, gridBagConstraints);

    scrMappingsManager.setViewportView(pnlMappingManager);

    add(scrMappingsManager, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents

  /***
   * The action for the search field and the search checkbox. If the checkbox
   * is not selected it cleans the filters. If it is selected it updates to the
   * current search string.
   */
  private void processFilterAction() {
    if (!(chkFilter.isSelected())) {
      applyFilters(new ArrayList<TreeModelFilter<OBDAMappingAxiom>>());
    }
    if (chkFilter.isSelected()) {
      try {
        List<TreeModelFilter<OBDAMappingAxiom>> filters = parseSearchString(txtFilter.getText());
        if (filters == null) {
          throw new Exception("Impossible to parse search string.");
        }
        applyFilters(filters);
      } catch (Exception e) {
        LoggerFactory.getLogger(this.getClass()).debug(e.getMessage(), e);
        JOptionPane.showMessageDialog(this, e.getMessage());
      }
    }
  }

  /***
   * Action for the filter checkbox
   *
   * @param evt
   * @throws Exception
   */
  private void chkFilterItemStateChanged(java.awt.event.ItemEvent evt) throws Exception {// GEN-FIRST:event_jCheckBox1ItemStateChanged
    processFilterAction();

  }// GEN-LAST:event_jCheckBox1ItemStateChanged

  /***
   * Action for key's entered in the search textbox
   *
   * @param evt
   * @throws Exception
   */
  private void sendFilters(java.awt.event.KeyEvent evt) throws Exception {// GEN-FIRST:event_sendFilters
    int key = evt.getKeyCode();
    if (key == java.awt.event.KeyEvent.VK_ENTER) {
      if (!chkFilter.isSelected()) {
        chkFilter.setSelected(true);
      } else {
        processFilterAction();
      }
    }

  }// GEN-LAST:event_sendFilters

  private void menuExecuteQueryActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_menuExecuteQueryActionPerformed
    // TODO add your handling code here:
    TreePath path = treMappingsTree.getSelectionPath();
    if (path == null) {
      return;
    }
    startExecuteQueryOfMapping(path);
  }// GEN-LAST:event_menuExecuteQueryActionPerformed

  private void menuValidateAllActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_menuValidateAllActionPerformed
    MappingValidationDialog outputField = new MappingValidationDialog(treMappingsTree);
    TreePath path[] = treMappingsTree.getSelectionPaths();
    if (path == null) {
      return;
    }

    for (int i = 0; i < path.length; i++) {
      Object o = path[i].getLastPathComponent();
      if (o instanceof MappingNode) {
        MappingNode node = (MappingNode) o;
        String id = node.getMappingID();
        MappingBodyNode body = node.getBodyNode();
        MappingHeadNode head = node.getHeadNode();
        RDBMSMappingValidator v;
        RDBMSSQLQuery rdbmssqlQuery = new RDBMSSQLQuery(body.getQuery());
        CQIE conjunctiveQuery = parse(head.getQuery());
        v = new RDBMSMappingValidator(apic, selectedSource, rdbmssqlQuery, conjunctiveQuery);
        Enumeration<String> errors = v.validate();
        if (!errors.hasMoreElements()) {
          String output = id + ": " + "valid  \n";
          outputField.addText(output, outputField.VALID);
        } else {
          while (errors.hasMoreElements()) {
            String ele = errors.nextElement();
            String output = id + ": " + ele + "  \n";
            if (ele.startsWith("N")) {
              outputField.addText(output, outputField.NONCRITICAL_ERROR);
            } else if (ele.startsWith("C")) {
              outputField.addText(output, outputField.CRITICAL_ERROR);
            } else {
              outputField.addText(output, outputField.NORMAL);
            }
          }
        }
      }
    }
  }// GEN-LAST:event_menuValidateAllActionPerformed

  private void menuValidateBodyActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_menuValidateBodyActionPerformed
    final MappingValidationDialog outputField = new MappingValidationDialog(treMappingsTree);

    Runnable action = new Runnable() {
      @Override
      public void run() {
        canceled = false;
        final TreePath path[] = treMappingsTree.getSelectionPaths();
        outputField.setVisible(true);
        if (path == null) {
          return;
        }
        outputField.addText("Validating " + path.length + " SQL queries.\n", outputField.NORMAL);
        for (int i = 0; i < path.length; i++) {
          final int index = i;
          Object o = path[index].getLastPathComponent();
          if (o instanceof MappingNode) {
            MappingNode node = (MappingNode) o;
            String id = node.getMappingID();
            MappingBodyNode body = node.getBodyNode();
            outputField.addText("  id: '" + id + "'... ", outputField.NORMAL);
            validator = new SQLQueryValidator(selectedSource, new RDBMSSQLQuery(body.getQuery()));
            long timestart = System.currentTimeMillis();

            if (canceled)
              return;

            if (validator.validate()) {
              long timestop = System.currentTimeMillis();
              String output = " valid  \n";
              outputField.addText("Time to query: " + ((timestop - timestart) / 1000) + " ms. Result: ", outputField.NORMAL);
              outputField.addText(output, outputField.VALID);
            } else {
              long timestop = System.currentTimeMillis();
              String output = " invalid Reason: " + validator.getReason().getMessage() + " \n";
              outputField.addText("Time to query: " + ((timestop - timestart) / 1000) + " ms. Result: ", outputField.NORMAL);
              outputField.addText(output, outputField.CRITICAL_ERROR);
            }
            validator.dispose();

            if (canceled)
              return;
          }
        }
      }
    };
    validatorThread = new Thread(action);
    validatorThread.start();

    Thread cancelThread = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!outputField.closed) {
          try {
            Thread.currentThread();
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        if (validatorThread.isAlive()) {
          try {
            Thread.currentThread();
            Thread.sleep(250);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          try {
            canceled = true;
            validator.cancelValidation();
          } catch (SQLException e) {
            e.printStackTrace();
          }
        }
      }
    });
    cancelThread.start();

  }// GEN-LAST:event_menuValidateBodyActionPerformed

  private void menuValidateHeadActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_menuValidateHeadActionPerformed
    // TODO add your handling code here:
  }// GEN-LAST:event_menuValidateHeadActionPerformed

  private void cmdDuplicateMappingActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_duplicateMappingButtonActionPerformed
    TreePath[] currentSelection = treMappingsTree.getSelectionPaths();
    if (currentSelection == null) {
      JOptionPane.showMessageDialog(this, "Please Select a Mapping first", "ERROR", JOptionPane.ERROR_MESSAGE);
    } else {
      if (JOptionPane.showConfirmDialog(this, "This will create copies of the selected mappings. \nNumber of mappings selected = "
          + treMappingsTree.getSelectionPaths().length + "\n Continue? ", "Copy confirmation", JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE) == JOptionPane.NO_OPTION) {
        return;
      }
      MappingController controller = mapc;
      URI current_srcuri = selectedSource.getSourceID();

      if (currentSelection != null) {
        for (int i = 0; i < currentSelection.length; i++) {
          TreePath current_path = currentSelection[i];
          MappingNode mapping = (MappingNode) current_path.getLastPathComponent();
          String id = (String) mapping.getUserObject();
          String new_id = controller.getNextAvailableDuplicateIDforMapping(current_srcuri, id);
          try {
            controller.duplicateMapping(current_srcuri, id, new_id);
          } catch (DuplicateMappingException e) {
            JOptionPane.showMessageDialog(this, "Duplicate Mapping: " + new_id);
          }
        }
      }
    }
  }// GEN-LAST:event_duplicateMappingButtonActionPerformed

  private void cmdRemoveMappingActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_removeMappingButtonActionPerformed
    removeMapping();
  }// GEN-LAST:event_removeMappingButtonActionPerformed

  private void removeMapping() {
    if (JOptionPane.showConfirmDialog(this, "This will delete ALL the selected mappings. \nNumber of mappings selected = "
        + treMappingsTree.getSelectionPaths().length + "\n Continue? ", "Delete confirmation", JOptionPane.WARNING_MESSAGE,
        JOptionPane.YES_NO_OPTION) == JOptionPane.CANCEL_OPTION) {
      return;
    }
    // The manager panel can handle multiple deletions.
    TreePath[] currentSelection = treMappingsTree.getSelectionPaths();
    MappingController controller = mapc;
    URI srcuri = selectedSource.getSourceID();

    if (currentSelection != null) {
      for (int i = 0; i < currentSelection.length; i++) {
        TreePath current_path = currentSelection[i];
        MappingNode mappingnode = (MappingNode) current_path.getLastPathComponent();
        controller.deleteMapping(srcuri, mappingnode.getMappingID());
      }
    }
  }

  private void cmdAddMappingActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_addMappingButtonActionPerformed
    addMapping();
  }// GEN-LAST:event_addMappingButtonActionPerformed

  private void addMapping() {
    
    MappingController controller = mapc;
    MappingTreeModel model = (MappingTreeModel) treMappingsTree.getModel();
    treMappingsTree.requestFocus();
    try {
      URI sourceId = selectedSource.getSourceID();
      String mappingId = controller.getNextAvailableMappingID(sourceId);     
      controller.insertMapping(selectedSource.getSourceID(), new RDBMSOBDAMappingAxiom(mappingId));
      MappingNode newnode = model.getMappingNode(mappingId);
      treMappingsTree.scrollPathToVisible(new TreePath(newnode.getBodyNode().getPath()));
      treMappingsTree.setSelectionPath(new TreePath(newnode.getPath()));
      if (!newnode.isLeaf()) {
        treMappingsTree.expandPath(new TreePath(newnode.getPath()));
      }
    } catch (NullPointerException e) {
      JOptionPane.showMessageDialog(null, "Select a data source first");
    } catch (DuplicateMappingException e) {
      e.printStackTrace(System.err);
      JOptionPane.showMessageDialog(this, "ERROR: duplicate mapping: " + e.getMessage());
      return;
    }
  }

  private void startEditHeadOfMapping(TreePath path) {
    treMappingsTree.setEditable(true);
    MappingNode mapping = (MappingNode) path.getLastPathComponent();
    MappingHeadNode head = mapping.getHeadNode();
    editedNode = head;
    treMappingsTree.startEditingAtPath(new TreePath(head.getPath()));
  }

  private void startEditBodyOfMapping(TreePath path) {
    treMappingsTree.setEditable(true);
    MappingNode mapping = (MappingNode) path.getLastPathComponent();
    MappingBodyNode body = mapping.getBodyNode();
    editedNode = body;
    treMappingsTree.startEditingAtPath(new TreePath(body.getPath()));
  }

  private void startExecuteQueryOfMapping(TreePath path) {
    final JDialog resultquery = new JDialog();
    MappingNode mapping = (MappingNode) path.getLastPathComponent();
    MappingBodyNode body = mapping.getBodyNode();
    SQLQueryPanel query_panel = new SQLQueryPanel(dsc, body.toString());

    resultquery.setSize(pnlMappingManager.getWidth(), pnlMappingManager.getHeight());
    resultquery.setLocationRelativeTo(null);
    resultquery.add(query_panel);
    resultquery.setVisible(true);
    resultquery.setTitle("Query Results");
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JCheckBox chkFilter;
  private javax.swing.JButton cmdAddMapping;
  private javax.swing.JButton cmdDuplicateMapping;
  private javax.swing.JButton cmdRemoveMapping;
  private javax.swing.JMenuItem menuExecuteQuery;
  private javax.swing.JPopupMenu menuMappings;
  private javax.swing.JMenuItem menuValidateAll;
  private javax.swing.JMenuItem menuValidateBody;
  private javax.swing.JMenuItem menuValidateHead;
  private javax.swing.JPanel pnlMappingButtons;
  private javax.swing.JPanel pnlMappingManager;
  private javax.swing.JScrollPane scrMappingsManager;
  private javax.swing.JScrollPane scrMappingsTree;
  private javax.swing.JTree treMappingsTree;
  private javax.swing.JTextField txtFilter;
  // End of variables declaration//GEN-END:variables

  @Override
  public void colorPeferenceChanged(String preference, Color col) {
    DefaultTreeModel model = (DefaultTreeModel) treMappingsTree.getModel();
    model.reload();
  }

  @Override
  public void fontFamilyPreferenceChanged(String preference, String font) {
    DefaultTreeModel model = (DefaultTreeModel) treMappingsTree.getModel();
    model.reload();
  }

  @Override
  public void fontSizePreferenceChanged(String preference, int size) {
    DefaultTreeModel model = (DefaultTreeModel) treMappingsTree.getModel();
    model.reload();
  }

  public void isBoldPreferenceChanged(String preference, Boolean isBold) {
    DefaultTreeModel model = (DefaultTreeModel) treMappingsTree.getModel();
    model.reload();
  }

  private void updateNode(String str) {
    MappingController con = mapc;
    URI sourceName = selectedSource.getSourceID();
    String nodeContent = (String) editedNode.getUserObject();
    if (editedNode instanceof MappingNode) {
      con.updateMapping(sourceName, nodeContent, str);
    } else if (editedNode instanceof MappingBodyNode) {
      MappingBodyNode node = (MappingBodyNode) editedNode;
      MappingNode parent = (MappingNode) node.getParent();
      Query b = new RDBMSSQLQuery(str);
      con.updateSourceQueryMapping(sourceName, parent.getMappingID(), b);
    } else if (editedNode instanceof MappingHeadNode) {
      MappingHeadNode node = (MappingHeadNode) editedNode;
      MappingNode parent = (MappingNode) node.getParent();
      Query h = parse(str);
      con.updateTargetQueryMapping(sourceName, parent.getMappingID(), h);
    }
  }

  public void shortCutChanged(String preference, String shortcut) {
    registerAction();
  }

  public void stopTreeEditing() {
    if (treMappingsTree.isEditing()) {
      MappingTreeNodeCellEditor editor = (MappingTreeNodeCellEditor) treMappingsTree.getCellEditor();
      if (editor.isInputValid()) {
        if (treMappingsTree.stopEditing()) {
          String txt = editor.getCellEditorValue().toString();
          updateNode(txt);
        }
      }
    }
  }

  public void applyChangedToNode(String txt) {
    updateNode(txt);
  }

  /***
   * Parses the string in the search field.
   *
   * @param textToParse
   * @return A list of filter objects or null if the string was empty or erroneous
   * @throws Exception
   */
  private List<TreeModelFilter<OBDAMappingAxiom>> parseSearchString(String textToParse) throws Exception {
    List<TreeModelFilter<OBDAMappingAxiom>> ListOfFilters =
      new ArrayList<TreeModelFilter<OBDAMappingAxiom>>();
    if (textToParse != null) {
      String[] textFilter = textToParse.split(",");
      for (int i = 0; i < textFilter.length; i++) {
        if (textFilter[i].contains(":")) {
          String[] components = textFilter[i].split(":");
          String head = components[0].trim();
          String body = components[1].trim();
          if ((body.startsWith("\"") && body.endsWith("\"")) ||
              (body.startsWith("'") && body.endsWith("'"))) {
            body = body.replaceAll("[\"|']", ""); // removes the quote signs.
            String[] keywords = body.split(" ");
            for (int j = 0; j < keywords.length; j++) {
              TreeModelFilter<OBDAMappingAxiom> filter =
                createFilter(head, keywords[j]);
              if (filter != null)
                ListOfFilters.add(filter);
            }
          }
          else {
            return null;
          }
        }
      }
    }
    return ListOfFilters;
  }

  /***
   * This function given the kind of filter and the string for it, is added to
   * a list of current filters.
   *
   * @param filter
   * @param strFilter
   */
  private TreeModelFilter<OBDAMappingAxiom> createFilter(String filter, String strFilter) {
    TreeModelFilter<OBDAMappingAxiom> typeOfFilter = null;
    if (filter.trim().equals(HEAD)) {
      typeOfFilter = new MappingHeadVariableTreeModelFilter(strFilter);
    } else if (filter.trim().equals(FUNCT)) {
      typeOfFilter = new MappingFunctorTreeModelFilter(strFilter);
    } else if (filter.trim().equals(PRED)) {
      typeOfFilter = new MappingPredicateTreeModelFilter(strFilter);
    } else if (filter.trim().equals(SQL)) {
      typeOfFilter = new MappingSQLStringTreeModelFilter(strFilter);
    } else if (filter.trim().equals(TEXT)) {
      typeOfFilter = new MappingStringTreeModelFilter(strFilter);
    } else if (filter.trim().equals(ID)) {
      typeOfFilter = new MappingIDTreeModelFilter(strFilter);
    }
    return typeOfFilter;
  }

  /***
   * This function add the list of current filters to the model and then the
   * Tree is refreshed shows the mappings after the filters have been applied
   *
   *
   * @param ListOfMappings
   */
  private void applyFilters(List<TreeModelFilter<OBDAMappingAxiom>> filters) {
    MappingTreeModel model = (MappingTreeModel) treMappingsTree.getModel();
    model.removeAllFilters();
    model.addFilters(filters);
    model.currentSourceChanged(selectedSource.getSourceID(), selectedSource.getSourceID());
  }

  private CQIE parse(String query) {
    CQIE cq = null;
    query = prepareQuery(query);
    try {
      datalogParser.parse(query);
      cq = datalogParser.getRule(0);
    }
    catch (RecognitionException e) {
      log.warn(e.getMessage());
    }
    return cq;
  }

  private String prepareQuery(String input) {
    String query = "";
    DatalogQueryHelper queryHelper =
      new DatalogQueryHelper(apic.getIOManager().getPrefixManager());

    String[] atoms = input.split(DatalogQueryHelper.DATALOG_IMPLY_SYMBOL, 2);
    if (atoms.length == 1)  // if no head
      query = queryHelper.getDefaultHead() + " " +
      DatalogQueryHelper.DATALOG_IMPLY_SYMBOL + " " +
      input;

    // Append the prefixes
    query = queryHelper.getPrefixes() + query;

    return query;
  }

  @Override
  public void datasourceChanged(DataSource oldSource, DataSource newSource)
  {
    this.selectedSource = newSource;

    // Update the mapping tree.
    MappingTreeModel model = (MappingTreeModel) treMappingsTree.getModel();
    URI oldSourceUri = null;
    if (oldSource != null) {
      oldSourceUri = oldSource.getSourceID();
    }
    URI newSourceUri = null;
    if (newSource != null) {
      newSourceUri = newSource.getSourceID();
    }
    model.currentSourceChanged(oldSourceUri, newSourceUri);
  }
}
