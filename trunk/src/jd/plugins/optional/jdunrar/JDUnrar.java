//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program  is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSSE the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://gnu.org/licenses/>.

package jd.plugins.optional.jdunrar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;

import jd.JDInit;
import jd.OptionalPluginWrapper;
import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.MenuItem;
import jd.config.SubConfiguration;
import jd.controlling.SingleDownloadController;
import jd.event.ControlEvent;
import jd.event.ControlListener;
import jd.gui.skins.simple.SimpleGUI;
import jd.gui.skins.simple.components.JDFileChooser;
import jd.gui.skins.simple.config.ConfigEntriesPanel;
import jd.gui.skins.simple.config.ConfigurationPopup;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginForHost;
import jd.plugins.PluginOptional;
import jd.plugins.PluginProgress;
import jd.utils.GetExplorer;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

public class JDUnrar extends PluginOptional implements ControlListener, UnrarListener {

    private static final String LOCALE_PREFIX = "plugins.optional.jdunrar.";
    private static final String DEFAULT_EXTRACT_PATH = "%DEFAULTDOWNLOADDIR%\\extracted\\%SUBFOLDER%\\%ARCHIVENAME%\\";

    public static int getAddonInterfaceVersion() {
        return 2;
    }

    /**
     * Wird als reihe für anstehende extracthjobs verwendet
     */
    private ArrayList<DownloadLink> queue;
    /**
     * Ist der startpart schon fertig, aber noch nicht alle anderen archivteile,
     * wird der link auf die wartequeue geschoben
     */
    // private ArrayList<DownloadLink> waitQueue;
    private int wrappersActive = 0;

    @SuppressWarnings("unchecked")
    public JDUnrar(PluginWrapper wrapper) {
        super(wrapper);
        this.queue = (ArrayList<DownloadLink>) this.getPluginConfig().getProperty(JDUnrarConstants.CONFIG_KEY_LIST, new ArrayList<DownloadLink>());

        // this.waitQueue = (ArrayList<DownloadLink>)
        // this.getPluginConfig().getProperty
        // (JDUnrarConstants.CONFIG_KEY_WAITLIST, new
        // ArrayList<DownloadLink>());

        initConfig();

    }

    /**
     * das controllevent fängt heruntergeladene file ab und wertet sie aus
     */
    @SuppressWarnings("unchecked")
    @Override
    public void controlEvent(ControlEvent event) {
        super.controlEvent(event);

        switch (event.getID()) {
        case ControlEvent.CONTROL_PLUGIN_INACTIVE:
            // Nur Hostpluginevents auswerten
            if (!(event.getSource() instanceof PluginForHost)) { return; }
            DownloadLink link = ((SingleDownloadController) event.getParameter()).getDownloadLink();
            link = findStartLink(link);

            if (link.getLinkStatus().hasStatus(LinkStatus.FINISHED)) {
                if (link.getFilePackage().isExtractAfterDownload()) {
                    if (getArchivePartType(link) == JDUnrarConstants.MULTIPART_START_PART || getArchivePartType(link) == JDUnrarConstants.SINGLE_PART_ARCHIVE) {
                        if (archiveIsComplete(link)) {
                            this.addToQueue(link);
                        }
                        // else {
                        // this.addToWaitQueue(link);
                        // }
                    }

                }
            }
            break;
        case ControlEvent.CONTROL_DOWNLOADLIST_ADDED_LINKS:
            ArrayList<DownloadLink> list = (ArrayList<DownloadLink>) event.getParameter();
            FilePackage old = null;
            for (DownloadLink l : list) {
                if (l.getFilePackage() == old) continue;
                old = l.getFilePackage();
                String[] pws = PasswordList.passwordStringtoArray(l.getFilePackage().getPassword());
                for (String pw : pws) {
                    PasswordList.addPassword(pw);
                }
            }
            PasswordList.save();
            break;

        case ControlEvent.CONTROL_LINKLIST_CONTEXT_MENU:
            ArrayList<MenuItem> items = (ArrayList<MenuItem>) event.getParameter();
            MenuItem m;
            if (event.getSource() instanceof DownloadLink) {
                link = (DownloadLink) event.getSource();

                items.add(m = new MenuItem(MenuItem.NORMAL, JDLocale.L(LOCALE_PREFIX + "linkmenu.extract", "Extract"), 1000).setActionListener(this));
                m.setEnabled(false);
                if (link.getLinkStatus().hasStatus(LinkStatus.FINISHED) && link.getName().matches(".*rar$")) m.setEnabled(true);
                if (new File(link.getFileOutput()).exists() && link.getName().matches(".*rar$")) m.setEnabled(true);

                m.setProperty("LINK", link);

                items.add(m = new MenuItem(MenuItem.NORMAL, JDLocale.L(LOCALE_PREFIX + "linkmenu.openextract", "Open directory"), 1002).setActionListener(this));
                m.setEnabled(link.getStringProperty("EXTRACTEDPATH") != null);
                m.setProperty("LINK", link);
            } else {
                FilePackage fp = (FilePackage) event.getSource();
                items.add(m = new MenuItem(MenuItem.NORMAL, JDLocale.L(LOCALE_PREFIX + "linkmenu.package.extract", "Extract package"), 1001).setActionListener(this));
                m.setProperty("PACKAGE", fp);

            }

            break;

        }

    }

    // /**
    // * prüft die Warteschlange ob nun archive komplett sind und entpackt
    // werden
    // * können.
    // *
    // */
    // private void checkWaitQueue() {
    // synchronized (waitQueue) {
    // for (int i = waitQueue.size() - 1; i >= 0; i--) {
    // if (archiveIsComplete(waitQueue.get(i))) {
    // this.addToQueue(waitQueue.remove(i));
    // this.getPluginConfig().setProperty(JDUnrarConstants.CONFIG_KEY_WAITLIST,
    // queue);
    // }
    // }
    // }
    //
    // }

    // /**
    // * Fügt downloadlinks, bei denen der startart zwar schon geladen ist, aber
    // * die folgeparts noch nicht zu einer wartequeue
    // *
    // * @param link
    // */
    // private void addToWaitQueue(DownloadLink link) {
    // synchronized (waitQueue) {
    // waitQueue.add(link);
    // this.getPluginConfig().setProperty(JDUnrarConstants.CONFIG_KEY_WAITLIST,
    // waitQueue);
    // this.getPluginConfig();
    // }
    // }

    /**
     * Prüft im zugehörigem Filepackage, ob noch downloadlinks vom archiv
     * ungeladen sind.
     * 
     * @param link
     * @return
     */
    private boolean archiveIsComplete(DownloadLink link) {
        DownloadLink l;
        String pattern = link.getFileOutput().replaceAll("\\.part[0-9]+.rar$", "");
        pattern = pattern.replaceAll("\\.rar$", "");
        for (int i = 0; i < link.getFilePackage().size(); i++) {
            l = link.getFilePackage().get(i);
            if (l.getFileOutput().startsWith(pattern) && (!l.getLinkStatus().hasStatus(LinkStatus.FINISHED) && !l.getLinkStatus().isFailed() && l.isEnabled())) return false;
        }
        return true;
    }

    /**
     * prüft um welchen archivtyp es sich handelt. Es wird
     * JDUnrarConstants.MULTIPART_START_PART
     * JDUnrarConstants.SINGLE_PART_ARCHIVE JDUnrarConstants.NO_RAR_ARCHIVE
     * JDUnrarConstants.NO_START_PART
     * 
     * @param link
     * @return
     */
    private int getArchivePartType(DownloadLink link) {
        if (link.getFileOutput().matches(".*part[0]*[1].rar$")) return JDUnrarConstants.MULTIPART_START_PART;
        if (!link.getFileOutput().matches(".*part[0-9]+.rar$") && link.getFileOutput().matches(".*rar$")) { return JDUnrarConstants.SINGLE_PART_ARCHIVE; }
        if (!link.getFileOutput().matches(".*rar$")) { return JDUnrarConstants.NO_RAR_ARCHIVE; }
        return JDUnrarConstants.NO_START_PART;
    }

    private DownloadLink findStartLink(DownloadLink link) {
        int type = getArchivePartType(link);
        switch (type) {
        case JDUnrarConstants.MULTIPART_START_PART:
        case JDUnrarConstants.SINGLE_PART_ARCHIVE:
        case JDUnrarConstants.NO_RAR_ARCHIVE:
            return link;
        }
        String filename = new Regex(link.getFileOutput(), "(.*)\\.part[0-9]+.rar$").getMatch(0);

        File file;
        if ((file = new File(filename + ".part1.rar")).exists()) {
        } else if ((file = new File(filename + ".part001.rar")).exists()) {
        } else if ((file = new File(filename + ".part000.rar")).exists()) {
        } else {
            return null;
        }
        DownloadLink dlink = JDUtilities.getController().getDownloadLinkByFileOutput(file);
        if (dlink == null) {
            System.out.print("DLink nicht gefunden.. erstelle Dummy");
            dlink = new DownloadLink(null, file.getName(), null, null, true);
            FilePackage fp = new FilePackage();
            fp.setDownloadDirectory(file.getParent());
            dlink.setFilePackage(fp);

        }
        return dlink;
    }

    private String getArchiveName(DownloadLink link) {
        String match = new Regex(new File(link.getFileOutput()).getName(), "(.*)\\.part[0]*[1].rar$").getMatch(0);
        if (match != null) return match;
        match = new Regex(new File(link.getFileOutput()).getName(), "(.*)\\.part[0-9]+.rar$").getMatch(0);
        if (match != null) return match;
        match = new Regex(new File(link.getFileOutput()).getName(), "(.*)\\.rar$").getMatch(0);
        return match;
    }

    /**
     * Fügt einen Link der Extractqueue hinzu
     * 
     * @param link
     */
    private void addToQueue(DownloadLink link) {
        synchronized (queue) {

            this.queue.add(link);
            this.getPluginConfig().setProperty(JDUnrarConstants.CONFIG_KEY_LIST, queue);
            this.getPluginConfig().save();
        }
        this.startExtraction();

    }

    /**
     * Startet das abwarbeiten der extractqueue
     */
    private synchronized void startExtraction() {

        if (wrappersActive > 2) return;
        DownloadLink link;
        if (queue.size() == 0) {

        return; }
        wrappersActive++;
        synchronized (queue) {
            link = queue.remove(0);
            this.getPluginConfig().setProperty(JDUnrarConstants.CONFIG_KEY_LIST, queue);
            this.getPluginConfig().save();
        }

        UnrarWrapper wrapper = new UnrarWrapper(link);
        File dl = this.getExtractToPath(link);
        link.setProperty("EXTRACTEDPATH", dl.getAbsolutePath());
        wrapper.addUnrarListener(this);
        wrapper.setExtractTo(dl);
        wrapper.setUnrarCommand(getPluginConfig().getStringProperty(JDUnrarConstants.CONFIG_KEY_UNRARCOMMAND));
        wrapper.setPasswordList(PasswordList.getPasswordList().toArray(new String[] {}));

        wrapper.start();

    }

    private File getExtractToPath(DownloadLink link) {
        String path = this.getPluginConfig().getStringProperty(JDUnrarConstants.CONFIG_KEY_UNRARPATH, DEFAULT_EXTRACT_PATH);

        String def = JDUtilities.getConfiguration().getDefaultDownloadDirectory();
        try {
            path = path.replace("%DEFAULTDOWNLOADDIR%", new File(def).getAbsolutePath());
            path = path.replace("%PACKAGENAME%", link.getFilePackage().getName());
            path = path.replace("%ARCHIVENAME%", getArchiveName(link));
            path = path.replace("%ARCHIVEPATH%", new File(link.getFileOutput()).getParent());
            path = path.replace("%HOSTER%", link.getHost());

            String dif = new File(def).getAbsolutePath().replace(new File(link.getFileOutput()).getParent(), "");
            if (new File(dif).isAbsolute()) {
                dif = "";
            }
            path = path.replace("%SUBFOLDER%", dif);
            File ret;
            path = path.replaceAll("[/]+", "\\");
            path = path.replaceAll("[\\\\]+", "\\\\");

            if (!(ret = new File(JDUtilities.validatePath(path))).isAbsolute()) return JDUtilities.getResourceFile(JDUtilities.validatePath(path));
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ArrayList<MenuItem> createMenuitems() {
        ArrayList<MenuItem> menu = new ArrayList<MenuItem>();
        MenuItem m;

        menu.add(m = new MenuItem(MenuItem.TOGGLE, JDLocale.L(LOCALE_PREFIX + "menu.toggle", "Activate"), 1).setActionListener(this));
        m.setSelected(this.getPluginConfig().getBooleanProperty("ACTIVATED", true));

        menu.add(m = new MenuItem(MenuItem.SEPARATOR));

        m = new MenuItem(MenuItem.NORMAL, JDLocale.L(LOCALE_PREFIX + "menu.extract.singlefils", "Extract archive(s)"), 21);
        m.setActionListener(this);
        menu.add(m);

        MenuItem queue;
        queue = new MenuItem(MenuItem.CONTAINER, JDLocale.L(LOCALE_PREFIX + "menu.queue", "Current Queue"), 3);
        m = new MenuItem(MenuItem.NORMAL, JDLocale.L(LOCALE_PREFIX + "menu.queue.start", "Start queue"), 30);

        m.setActionListener(this);
        queue.addMenuItem(m);
        m = new MenuItem(MenuItem.NORMAL, JDLocale.L(LOCALE_PREFIX + "menu.queue.clear", "Clear queue"), 31);

        m.setActionListener(this);
        queue.addMenuItem(m);
        queue.addMenuItem(m = new MenuItem(MenuItem.SEPARATOR));
        int i = 0;
        for (DownloadLink link : this.queue) {
            m = new MenuItem(MenuItem.NORMAL, JDLocale.LF(LOCALE_PREFIX + "menu.queue.extract", "Extract %s", link.getName()), 3000 + i);
            m.setActionListener(this);
            queue.addMenuItem(m);
            i++;

        }
        menu.add(queue);

        menu.add(m = new MenuItem(MenuItem.SEPARATOR));

        menu.add(m = new MenuItem(MenuItem.NORMAL, JDLocale.L(LOCALE_PREFIX + "menu.config", "Settings"), 4).setActionListener(this));

        return menu;
    }

    public void actionPerformed(ActionEvent e) {

        if (e.getSource() instanceof MenuItem) {
            menuitemActionPerformed(e, (MenuItem) e.getSource());
        }

    }

    private void menuitemActionPerformed(ActionEvent e, MenuItem source) {
        SubConfiguration cfg = this.getPluginConfig();
        switch (source.getActionID()) {
        case 0:
            boolean newValue;
            cfg.setProperty("ACTIVATED", newValue = !cfg.getBooleanProperty("ACTIVATED", true));
            if (newValue) {
                JDUtilities.getController().addControlListener(this);
            } else {
                JDUtilities.getController().removeControlListener(this);
            }
            break;
        case 21:
            JDFileChooser fc = new JDFileChooser("_JDUNRAR_");
            fc.setMultiSelectionEnabled(true);
            FileFilter ff = new FileFilter() {

                public boolean accept(File pathname) {
                    if (pathname.getName().matches(".*part[0]*[1].rar$")) return true;
                    if (!pathname.getName().matches(".*part[0-9]+.rar$") && pathname.getName().matches(".*rar$")) { return true; }
                    if (pathname.isDirectory()) return true;
                    return false;
                }

                @Override
                public String getDescription() {
                    // TODO Auto-generated method stub
                    return "Rar-Startvolumes";
                }

            };
            fc.setFileFilter((javax.swing.filechooser.FileFilter) ff);
            fc.showSaveDialog(SimpleGUI.CURRENTGUI.getFrame());
            File[] list = fc.getSelectedFiles();
            if (list == null) return;
            DownloadLink link;
            for (File archiveStartFile : list) {
                link = JDUtilities.getController().getDownloadLinkByFileOutput(archiveStartFile);

                if (link == null) {
                    link = new DownloadLink(null, archiveStartFile.getName(), "dummy.de", "", true);
                    link.setDownloadSize(archiveStartFile.length());
                    FilePackage fp = new FilePackage();
                    fp.setDownloadDirectory(archiveStartFile.getParent());
                    link.setFilePackage(fp);
                }
                link = this.findStartLink(link);
                if (link == null) {
                    continue;
                }
                final DownloadLink finalLink = link;
                System.out.print("queued to extract: " + archiveStartFile);
                new Thread() {
                    public void run() {

                        addToQueue(finalLink);
                    }
                }.start();

            }
            break;

        case 30:
            this.startExtraction();
            break;

        case 4:
            ConfigEntriesPanel cpanel = new ConfigEntriesPanel(config);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JPanel(), BorderLayout.NORTH);
            panel.add(cpanel, BorderLayout.CENTER);
            ConfigurationPopup pop = new ConfigurationPopup(SimpleGUI.CURRENTGUI.getFrame(), cpanel, panel);
            pop.setLocation(JDUtilities.getCenterOfComponent(SimpleGUI.CURRENTGUI.getFrame(), pop));
            pop.setVisible(true);
            break;
        case 31:
            this.queue.clear();
            break;

        case 1000:

            link = this.findStartLink((DownloadLink) source.getProperty("LINK"));
            if (link == null) { return; }
            final DownloadLink finalLink = link;
            System.out.print("queued to extract: " + link);
            new Thread() {
                public void run() {

                    addToQueue(finalLink);
                }
            }.start();
            break;

        case 1001:

            FilePackage fp = (FilePackage) source.getProperty("PACKAGE");
            ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            for (DownloadLink l : fp.getDownloadLinks()) {
                if (l.getLinkStatus().hasStatus(LinkStatus.FINISHED)) {
                    if (l.getName().matches(".*part[0]*[1].rar$") || (!l.getName().matches(".*part[0-9]+.rar$") && l.getName().matches(".*rar$"))) {
                        links.add(l);
                    }

                }

            }
            if (links.size() <= 0) return;

            for (DownloadLink link0 : links) {
                link = link0;

                link = this.findStartLink(link);
                if (link == null) {
                    continue;
                }
                final DownloadLink finalLink0 = link;
                System.out.print("queued to extract: " + link);
                new Thread() {
                    public void run() {
                        addToQueue(finalLink0);
                    }
                }.start();

            }
            break;

        case 1002:

            link = this.findStartLink((DownloadLink) source.getProperty("LINK"));
            if (link == null) { return; }
            String path = link.getStringProperty("EXTRACTEDPATH");

            try {
                new GetExplorer().openExplorer(new File(path));
            } catch (Exception ec) {
            }

            break;
        }
        if (source.getActionID() >= 3000) {
            int id = source.getActionID() - 3000;

            if (queue.size() <= id) return;
            wrappersActive++;
            DownloadLink link;
            synchronized (queue) {
                link = queue.remove(id);
                this.getPluginConfig().setProperty(JDUnrarConstants.CONFIG_KEY_LIST, queue);
                this.getPluginConfig().save();
            }

            UnrarWrapper wrapper = new UnrarWrapper(link);
            wrapper.addUnrarListener(this);
            wrapper.setUnrarCommand(getPluginConfig().getStringProperty(JDUnrarConstants.CONFIG_KEY_UNRARCOMMAND));
            wrapper.setPasswordList(PasswordList.getPasswordList().toArray(new String[] {}));

            wrapper.start();
        }

    }

    @Override
    public String getHost() {
        return JDLocale.L("plugins.optional.jdunrar.name", "JD-Unrar");
    }

    @Override
    public String getRequirements() {
        return "JRE 1.5+";
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision: 2851 $", "\\$Revision: ([\\d]*?) \\$").getMatch(0);
        return ret == null ? "0.0" : ret;
    }

    @Override
    public boolean initAddon() {
        if (this.getPluginConfig().getBooleanProperty("ACTIVATED", true)) {
            JDUtilities.getController().addControlListener(this);
        }
        return true;

    }

    public void initConfig() {
        SubConfiguration subConfig = getPluginConfig();

        config.addEntry(new ConfigEntry(ConfigContainer.TYPE_BROWSEFILE, subConfig, JDUnrarConstants.CONFIG_KEY_UNRARCOMMAND, JDLocale.L("gui.config.unrar.cmd", "UnRAR command")));
        ConfigEntry ce;
        config.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, subConfig, JDUnrarConstants.CONFIG_KEY_UNRARPATH, JDLocale.L("gui.config.unrar.path", "Extract to")));
        ce.setDefaultValue(DEFAULT_EXTRACT_PATH);

        ConfigContainer pws = new ConfigContainer(this, JDLocale.L(LOCALE_PREFIX + "config.passwordtab", "List of passwords"));
        config.addEntry(new ConfigEntry(ConfigContainer.TYPE_CONTAINER, pws));
        pws.addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTAREA, JDUtilities.getSubConfig(PasswordList.PROPERTY_PASSWORDLIST), "LIST", JDLocale.L(LOCALE_PREFIX + "config.passwordlist", "List of all passwords. Each line one password")));

    }

    @Override
    public void onExit() {
        JDUtilities.getController().removeControlListener(this);
    }

    public void onUnrarEvent(int id, UnrarWrapper wrapper) {
        switch (id) {
        case JDUnrarConstants.WRAPPER_EXTRACTION_FAILED:
            if (wrapper.getException() != null) {

                wrapper.getDownloadLink().getLinkStatus().setErrorMessage("Extract failed: " + wrapper.getException().getMessage());
                wrapper.getDownloadLink().requestGuiUpdate();
            } else {
                wrapper.getDownloadLink().getLinkStatus().setErrorMessage("Extract failed");
                wrapper.getDownloadLink().requestGuiUpdate();
            }
            this.onFinished(wrapper);

            break;
        case JDUnrarConstants.WRAPPER_FAILED_PASSWORD:
            wrapper.getDownloadLink().getLinkStatus().setErrorMessage("Extract failed(password)");
            wrapper.getDownloadLink().requestGuiUpdate();
            this.onFinished(wrapper);

            break;

        case JDUnrarConstants.WRAPPER_CRACK_PASSWORD:

            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Cracking password");
            wrapper.getDownloadLink().getLinkStatus().setStatusText("Crack password");
            wrapper.getDownloadLink().requestGuiUpdate();
            break;
        case JDUnrarConstants.WRAPPER_NEW_STATUS:
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "New status " + wrapper.getStatus());
            break;
        case JDUnrarConstants.WRAPPER_START_OPEN_ARCHIVE:
            wrapper.getDownloadLink().getLinkStatus().setStatusText("Open archive");
            wrapper.getDownloadLink().requestGuiUpdate();
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Start opening archive");
            break;
        case JDUnrarConstants.WRAPPER_OPEN_ARCHIVE_SUCCESS:
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Archive opened successfull");
            break;
        case JDUnrarConstants.WRAPPER_PASSWORD_FOUND:
            // progress.get(wrapper).setColor(Color.GREEN);
            wrapper.getDownloadLink().getLinkStatus().setStatusText("Password found");
            wrapper.getDownloadLink().requestGuiUpdate();
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Password found " + wrapper.getPassword());
            break;
        case JDUnrarConstants.WRAPPER_ON_PROGRESS:
            // progress.get(wrapper).setRange(wrapper.getTotalSize());
            // progress.get(wrapper).setStatus(wrapper.getExtractedSize());
            wrapper.getDownloadLink().getLinkStatus().setStatusText("Extracting");

            if (wrapper.getDownloadLink().getPluginProgress() == null) {
                wrapper.getDownloadLink().setPluginProgress(new PluginProgress(wrapper.getExtractedSize(), wrapper.getTotalSize(), Color.YELLOW.darker()));
                //                
            } else {
                wrapper.getDownloadLink().getPluginProgress().setCurrent(wrapper.getExtractedSize());
                //               
            }
            wrapper.getDownloadLink().requestGuiUpdate();
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Progress: " +
            // JDUtilities.getPercent(wrapper.getExtractedSize(),
            // wrapper.getTotalSize()));
            break;
        case JDUnrarConstants.WRAPPER_START_EXTRACTION:
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Extraction started");
            break;
        case JDUnrarConstants.WRAPPER_STARTED:
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Started Unrarprocess");
            break;
        case JDUnrarConstants.WRAPPER_EXTRACTION_FAILED_CRC:
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "CRC Failure");
            // progress.get(wrapper).setColor(Color.RED);
            ArrayList<DownloadLink> list = this.getArchiveList(wrapper.getDownloadLink());
            DownloadLink crc = list.size() >= wrapper.getCurrentVolume() ? list.get(wrapper.getCurrentVolume()) : null;
            wrapper.getDownloadLink().getLinkStatus().setErrorMessage("Extract: failed(CRC)");
            // wrapper.getDownloadLink().reset();
            wrapper.getDownloadLink().requestGuiUpdate();
            if (crc != null) {
                // crc.reset();
                crc.getLinkStatus().removeStatus(LinkStatus.FINISHED);
                crc.getLinkStatus().addStatus(LinkStatus.ERROR_DOWNLOAD_FAILED);
                crc.getLinkStatus().setErrorMessage("Extract: failed(CRC)");
                crc.requestGuiUpdate();
            } else {
                for (DownloadLink link : list) {
                    if (link == null) continue;
                    link.getLinkStatus().setErrorMessage("Extract: failed(CRC)");
                    link.requestGuiUpdate();
                }
            }
            this.onFinished(wrapper);

            break;

        case JDUnrarConstants.WRAPPER_PROGRESS_SINGLE_FILE_FINISHED:
            // progress.get(wrapper).setColor(Color.YELLOW);

            // progress.get(wrapper).setColor(Color.GREEN);
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "Progress. SingleFile finished: " +
            // wrapper.getCurrentFile());
            break;
        case JDUnrarConstants.WRAPPER_FINISHED_SUCCESSFULL:
            // progress.get(wrapper).setStatusText(wrapper.getFile().getName() +
            // ": " + "SUCCESSFULL");
            // progress.get(wrapper).setColor(Color.GREEN);

            list = this.getArchiveList(wrapper.getDownloadLink());

            for (DownloadLink link : list) {
                if (link == null) continue;
                link.getLinkStatus().setStatusText("Extract: OK");
                link.requestGuiUpdate();
            }

            this.onFinished(wrapper);

            break;

        default:
            System.out.println("id ");

        }
    }

    private ArrayList<DownloadLink> getArchiveList(DownloadLink downloadLink) {
        ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        ret.add(downloadLink);
        File file;
        switch (this.getArchivePartType(downloadLink)) {
        case JDUnrarConstants.MULTIPART_START_PART:
            String name = this.getArchiveName(downloadLink);
            int i = 2;

            while ((file = new File(new File(downloadLink.getFileOutput()).getParentFile(), name + ".part" + i + ".rar")).exists()) {
                ret.add(JDUtilities.getController().getDownloadLinkByFileOutput(file));
                i++;
            }
            break;

        case JDUnrarConstants.SINGLE_PART_ARCHIVE:

            break;

        }
        return ret;

    }

    private void onFinished(UnrarWrapper wrapper) {
        // progress.get(wrapper).finalize(3000l);
        this.wrappersActive--;
        wrapper.getDownloadLink().setPluginProgress(null);
        this.startExtraction();

    }

    public static void main(String[] args) {
        new JDInit().initController();
        OptionalPluginWrapper plgWrapper = new OptionalPluginWrapper("jdunrar.JDUnrar", 1.5);
        JDUnrar unrar = new JDUnrar(plgWrapper);
        unrar.initAddon();
        DownloadLink link = new DownloadLink(null, "mdk-heroes-203.part1.rar", "host.de", "http://download.bla", true);
        link.setDownloadSize(100 * 1024 * 1024);
        FilePackage fp = new FilePackage();
        fp.setDownloadDirectory("D:\\serien\\heroes");

        link.setFilePackage(fp);
        fp.setPassword("serienjunkies.org");
        unrar.addToQueue(link);

    }
}