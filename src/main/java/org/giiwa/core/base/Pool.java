package org.giiwa.core.base;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.core.bean.TimeStamp;
import org.giiwa.core.task.Task;

/**
 * A general pool class, that can be for database , or something else
 * 
 * @author joe
 *
 * @param <E>
 */
public class Pool<E> {
  static Log              log     = LogFactory.getLog(Pool.class);

  private List<E>         list    = new ArrayList<E>();
  private int             initial = 10;
  private int             max     = 10;
  private int             created = 0;

  private IPoolFactory<E> factory = null;

  /**
   * create a pool by initial, max and factory
   * 
   * @param initial
   * @param max
   * @param factory
   * @return
   */
  public static  <E> Pool<E> create(int initial, int max, IPoolFactory<E> factory) {
    Pool<E> p = new Pool<E>();
    p.initial = initial;
    p.max = max;
    p.factory = factory;
    new Task() {
      @Override
      public void onExecute() {
        p.init();
      }
    }.schedule(0);
    return p;
  }

  private void init() {
    for (int i = 0; i < initial; i++) {
      E t = factory.create();
      if (t != null) {
        synchronized (list) {
          list.add(t);
        }
      }
    }
    created = list.size();
  }

  /**
   * release a object to the pool
   * 
   * @param t
   */
  public void release(E t) {
    if (t == null) {
      created--;
    } else {
      factory.cleanup(t);
      synchronized (list) {
        list.add(t);
      }
    }
  }

  /**
   * destroy the pool, and destroy all the object in the pool
   */
  public void destroy() {
    synchronized (list) {
      for (E e : list) {
        factory.destroy(e);
      }
      list.clear();
    }
  }

  /**
   * get a object from the pool, if meet the max, then wait till timeout
   * 
   * @param timeout
   * @return
   */
  public E get(long timeout) {
    try {
      TimeStamp t = TimeStamp.create();

      long t1 = timeout;

      synchronized (list) {
        while (t1 > 0) {
          if (list.size() > 0) {
            return list.remove(0);
          } else {
            t1 = timeout - t.past();
            if (t1 > 0) {

              // log.debug("t1=" + t1);
              //
              if (created < max) {
                new Task() {

                  @Override
                  public void onExecute() {
                    E e = factory.create();
                    if (e != null) {
                      created++;
                      release(e);
                    }

                  }
                }.schedule(0);
              }

              list.wait(t1);
            }
          }
        }
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }

    return null;
  }

  /**
   * the pool factory interface using to create E object in pool
   * 
   * @author wujun
   *
   * @param <E>
   */
  public interface IPoolFactory<E> {

    /**
     * create a object
     * 
     * @return
     */
    public E create();

    /**
     * clean up a object after used
     * 
     * @param t
     */
    public void cleanup(E t);

    /**
     * destroy a object
     * 
     * @param t
     */
    public void destroy(E t);
  }
}
