package com.linkedin.clustermanager.agent.file;

import static com.linkedin.clustermanager.CMConstants.ChangeType.CONFIG;
import static com.linkedin.clustermanager.CMConstants.ChangeType.CURRENT_STATE;
import static com.linkedin.clustermanager.CMConstants.ChangeType.EXTERNAL_VIEW;
import static com.linkedin.clustermanager.CMConstants.ChangeType.IDEAL_STATE;
import static com.linkedin.clustermanager.CMConstants.ChangeType.LIVE_INSTANCE;
import static com.linkedin.clustermanager.CMConstants.ChangeType.MESSAGE;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.zookeeper.Watcher.Event.EventType;

import com.linkedin.clustermanager.CMConstants.ChangeType;
import com.linkedin.clustermanager.ClusterManager;
import com.linkedin.clustermanager.ConfigChangeListener;
import com.linkedin.clustermanager.ControllerChangeListener;
import com.linkedin.clustermanager.CurrentStateChangeListener;
import com.linkedin.clustermanager.ExternalViewChangeListener;
import com.linkedin.clustermanager.IdealStateChangeListener;
import com.linkedin.clustermanager.LiveInstanceChangeListener;
import com.linkedin.clustermanager.MessageListener;
import com.linkedin.clustermanager.NotificationContext;
import com.linkedin.clustermanager.PropertyType;
import com.linkedin.clustermanager.ZNRecord;
import com.linkedin.clustermanager.model.CurrentState;
import com.linkedin.clustermanager.model.ExternalView;
import com.linkedin.clustermanager.model.IdealState;
import com.linkedin.clustermanager.model.InstanceConfig;
import com.linkedin.clustermanager.model.LiveInstance;
import com.linkedin.clustermanager.model.Message;
import com.linkedin.clustermanager.store.PropertyChangeListener;
import com.linkedin.clustermanager.store.PropertyStoreException;
import com.linkedin.clustermanager.store.file.FilePropertyStore;
import com.linkedin.clustermanager.util.CMUtil;

// TODO remove code duplication: CallbackHandler and CallbackHandlerForFile
public class CallbackHandlerForFile implements PropertyChangeListener<ZNRecord>
{

  private static Logger logger = Logger.getLogger(CallbackHandlerForFile.class);

  private final String _path;
  private final Object _listener;
  private final EventType[] _eventTypes;
  private final ChangeType _changeType;
  private final FileBasedDataAccessor _accessor;
  private final AtomicLong lastNotificationTimeStamp;
  private final ClusterManager _manager;
  private final FilePropertyStore<ZNRecord> _store;

  public CallbackHandlerForFile(ClusterManager manager, String path, Object listener,
                                EventType[] eventTypes, ChangeType changeType)
  {
    this._manager = manager;
    this._accessor = (FileBasedDataAccessor) manager.getDataAccessor();
    this._path = path;
    this._listener = listener;
    this._eventTypes = eventTypes;
    this._changeType = changeType;
    this._store = (FilePropertyStore<ZNRecord>) _accessor.getStore();
    lastNotificationTimeStamp = new AtomicLong(System.nanoTime());

    init();
  }

  public Object getListener()
  {
    return _listener;
  }

  public Object getPath()
  {
    return _path;
  }

  public void invoke(NotificationContext changeContext) throws Exception
  {
    // This allows the listener to work with one change at a time
    synchronized (_listener)
    {
      if (logger.isDebugEnabled())
      {
        logger.debug(Thread.currentThread().getId() + " START:INVOKE "
            + changeContext.getPathChanged() + " listener:"
            + _listener.getClass().getCanonicalName());
      }
      if (_changeType == IDEAL_STATE)
      {
        // System.err.println("ideal state change");
        IdealStateChangeListener idealStateChangeListener =
            (IdealStateChangeListener) _listener;
        subscribeForChanges(changeContext, true, true);
        List<IdealState> idealStates =
            _accessor.getChildValues(IdealState.class, PropertyType.IDEALSTATES);
        idealStateChangeListener.onIdealStateChange(idealStates, changeContext);

      }
      else if (_changeType == CONFIG)
      {

        ConfigChangeListener configChangeListener = (ConfigChangeListener) _listener;
        subscribeForChanges(changeContext, true, true);
        List<InstanceConfig> configs =
            _accessor.getChildValues(InstanceConfig.class, PropertyType.CONFIGS);
        configChangeListener.onConfigChange(configs, changeContext);

      }
      else if (_changeType == LIVE_INSTANCE)
      {
        LiveInstanceChangeListener liveInstanceChangeListener =
            (LiveInstanceChangeListener) _listener;
        subscribeForChanges(changeContext, true, false);
        List<LiveInstance> liveInstances =
            _accessor.getChildValues(LiveInstance.class, PropertyType.LIVEINSTANCES);
        liveInstanceChangeListener.onLiveInstanceChange(liveInstances, changeContext);

      }
      else if (_changeType == CURRENT_STATE)
      {
        CurrentStateChangeListener currentStateChangeListener;
        currentStateChangeListener = (CurrentStateChangeListener) _listener;
        subscribeForChanges(changeContext, true, true);
        String instanceName = CMUtil.getInstanceNameFromPath(_path);
        String[] pathParts = _path.split("/");
        List<CurrentState> currentStates =
            _accessor.getChildValues(CurrentState.class,
                                     PropertyType.CURRENTSTATES,
                                     instanceName,
                                     pathParts[pathParts.length - 1]);
        currentStateChangeListener.onStateChange(instanceName,
                                                 currentStates,
                                                 changeContext);

      }
      else if (_changeType == MESSAGE)
      {
        MessageListener messageListener = (MessageListener) _listener;
        subscribeForChanges(changeContext, true, false);
        String instanceName = _manager.getInstanceName();
        List<Message> messages =
            _accessor.getChildValues(Message.class, PropertyType.MESSAGES, instanceName);
        messageListener.onMessage(instanceName, messages, changeContext);
      }
      else if (_changeType == EXTERNAL_VIEW)
      {
        ExternalViewChangeListener externalViewListener =
            (ExternalViewChangeListener) _listener;
        subscribeForChanges(changeContext, true, true);
        List<ExternalView> externalViewList =
            _accessor.getChildValues(ExternalView.class, PropertyType.EXTERNALVIEW);
        externalViewListener.onExternalViewChange(externalViewList, changeContext);
      }
      else if (_changeType == ChangeType.CONTROLLER)
      {
        ControllerChangeListener controllerChangelistener =
            (ControllerChangeListener) _listener;
        subscribeForChanges(changeContext, true, false);
        controllerChangelistener.onControllerChange(changeContext);
      }

      if (logger.isDebugEnabled())
      {
        logger.debug(Thread.currentThread().getId() + " END:INVOKE "
            + changeContext.getPathChanged() + " listener:"
            + _listener.getClass().getCanonicalName());
      }
    }
  }

  private void subscribeForChanges(NotificationContext changeContext,
                                   boolean watchParent,
                                   boolean watchChild)
  {
    if (changeContext.getType() == NotificationContext.Type.INIT)
    {
      try
      {
        // _accessor.subscribeForPropertyChange(_path, this);
        _store.subscribeForPropertyChange(_path, this);
      }
      catch (PropertyStoreException e)
      {
        logger.error("fail to subscribe for changes" + "\nexception:" + e);
      }
    }
  }

  public EventType[] getEventTypes()
  {
    return _eventTypes;
  }

  // this will invoke the listener so that it sets up the initial values from
  // the file property store if any exists
  public void init()
  {
    updateNotificationTime(System.nanoTime());
    try
    {
      NotificationContext changeContext = new NotificationContext(_manager);
      changeContext.setType(NotificationContext.Type.INIT);
      invoke(changeContext);
    }
    catch (Exception e)
    {
      // TODO handle exception
      logger.error("fail to init", e);
    }
  }

  public void reset()
  {
    try
    {
      NotificationContext changeContext = new NotificationContext(_manager);
      changeContext.setType(NotificationContext.Type.FINALIZE);
      invoke(changeContext);
    }
    catch (Exception e)
    {
      // TODO handle exception
      logger.error("fail to reset" + "\nexception:" + e);
      // ZKExceptionHandler.getInstance().handle(e);
    }
  }

  private void updateNotificationTime(long nanoTime)
  {
    long l = lastNotificationTimeStamp.get();
    while (nanoTime > l)
    {
      boolean b = lastNotificationTimeStamp.compareAndSet(l, nanoTime);
      if (b)
      {
        break;
      }
      else
      {
        l = lastNotificationTimeStamp.get();
      }
    }
  }

  @Override
  public void onPropertyChange(String key)
  {
    // System.err.println("on property change, key:" + key + ", path:" + _path);
    try
    {
      if (needToNotify(key))
      {
        // System.err.println("notified on property change, key:" + key + ", path:" +
        // path);

        updateNotificationTime(System.nanoTime());
        NotificationContext changeContext = new NotificationContext(_manager);
        changeContext.setType(NotificationContext.Type.CALLBACK);
        invoke(changeContext);
      }
    }
    catch (Exception e)
    {
      // TODO handle exception
      // ZKExceptionHandler.getInstance().handle(e);
      logger.error("fail onPropertyChange", e);
    }
  }

  private boolean needToNotify(String key)
  {
    boolean ret = false;
    switch (_changeType)
    {
    // both child/data changes matter
    case IDEAL_STATE:
    case CURRENT_STATE:
    case CONFIG:
      ret = key.startsWith(_path);
      break;
    // only child changes matter
    case LIVE_INSTANCE:
    case MESSAGE:
    case EXTERNAL_VIEW:
    case CONTROLLER:
      // ret = key.equals(_path);
      ret = key.startsWith(_path);
      break;
    default:
      break;
    }

    return ret;
  }
}
