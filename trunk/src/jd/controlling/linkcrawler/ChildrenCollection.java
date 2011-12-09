package jd.controlling.linkcrawler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.appwork.exceptions.WTFException;
import org.jdownloader.DomainInfo;

public class ChildrenCollection extends ArrayList<CrawledLink> {
    private long                         fileSize;
    private HashMap<DomainInfo, Integer> hostCountMap;

    private HashSet<DomainInfo>          domainList;
    private DomainInfo[]                 domainInfos;
    private CrawledPackage               crawledPackage;
    private HashSet<CrawledLink>         enabled;
    private HashMap<CrawledLink, Long>   sizes;

    public ChildrenCollection(CrawledPackage crawledPackage) {
        this.fileSize = 0l;
        this.crawledPackage = crawledPackage;
        hostCountMap = new HashMap<DomainInfo, Integer>();
        domainList = new HashSet<DomainInfo>();
        enabled = new HashSet<CrawledLink>();
        sizes = new HashMap<CrawledLink, Long>();
    }

    @Override
    public CrawledLink set(int index, CrawledLink element) {
        CrawledLink old = get(index);
        try {
            return super.set(index, element);
        } finally {
            if (old != null) {
                removeInfo(old);
            }
            addInfo(element);
        }
    }

    @Override
    public boolean add(CrawledLink e) {
        try {

            return super.add(e);
        } finally {

            addInfo(e);
        }
    }

    @Override
    public void add(int index, CrawledLink element) {

        try {

            super.add(index, element);
        } finally {

            addInfo(element);
        }
    }

    private void addInfo(CrawledLink element) {
        synchronized (this) {
            // domain
            DomainInfo domainInfo = element.getDomainInfo();
            Integer current = hostCountMap.get(domainInfo);
            if (current == null) current = 0;
            hostCountMap.put(domainInfo, current + 1);
            domainList.add(domainInfo);
            domainInfos = domainList.toArray(new DomainInfo[] {});
            // enabled
            if (element.isEnabled()) enabled.add(element);

            // size
            sizes.put(element, element.getSize());
            fileSize += element.getSize();

            // System.out.println(element + " add: " + crawledPackage.getName()
            // + " : " + hostCountMap + " " + domainList);
        }

    }

    private void removeInfo(CrawledLink element) {

        synchronized (this) {
            // domain
            DomainInfo domainInfo = element.getDomainInfo();
            Integer current = hostCountMap.get(domainInfo);
            if (current == null || current < 1) throw new WTFException("cannot remove element. Is not there");

            if (current == 1) {
                hostCountMap.remove(domainInfo);
                domainList.remove(domainInfo);
            } else {
                hostCountMap.put(domainInfo, current - 1);
            }
            domainInfos = domainList.toArray(new DomainInfo[] {});
            // enabled

            enabled.remove(element);

            // size

            fileSize -= sizes.get(element);
            if (fileSize < 0) throw new WTFException("Filesize cannot be less than 0");
            // System.out.println(element + " rem: " + crawledPackage.getName()
            // + " : " + hostCountMap + " " + domainList);
        }
    }

    @Override
    public CrawledLink remove(int index) {
        CrawledLink ret = super.remove(index);
        removeInfo(ret);
        return ret;
    }

    @Override
    public boolean remove(Object o) {
        try {
            return super.remove(o);
        } finally {
            if (o instanceof CrawledLink) {
                removeInfo((CrawledLink) o);
            }
        }
    }

    @Override
    public void clear() {
        CrawledLink[] old = this.toArray(new CrawledLink[] {});
        try {

            super.clear();
        } finally {
            for (CrawledLink c : old) {
                removeInfo(c);
            }
        }
    }

    @Override
    public boolean addAll(Collection<? extends CrawledLink> c) {
        try {

            return super.addAll(c);

        } finally {
            for (CrawledLink cc : c) {
                addInfo(cc);

            }
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends CrawledLink> c) {
        try {
            return super.addAll(index, c);
        } finally {
            for (CrawledLink cc : c) {
                addInfo(cc);

            }
        }
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        ArrayList<CrawledLink> old = new ArrayList<CrawledLink>();
        for (int i = fromIndex; i < toIndex; i++) {
            old.add(get(i));
        }
        super.removeRange(fromIndex, toIndex);

        for (CrawledLink c : old) {
            if (c != null) removeInfo(c);
        }
    }

    @Override
    public boolean removeAll(Collection<?> old) {
        try {
            return super.removeAll(old);

        } finally {
            for (Object c : old) {
                if (c != null && c instanceof CrawledLink) removeInfo((CrawledLink) c);
            }
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new WTFException("Not supported");
        // return super.retainAll(c);
    }

    public void updateInfo(CrawledLink crawledLink) {
        synchronized (this) {
            removeInfo(crawledLink);
            addInfo(crawledLink);

        }
    }

    public DomainInfo[] getDomainInfos() {
        return domainInfos;
    }

    public boolean isEnabled() {
        return enabled.size() > 0;
    }

    public long getFileSize() {
        return fileSize;
    }

}