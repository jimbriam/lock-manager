def userInstalled() {
  log.debug "Installed with settings: ${settings}"
  userInitialize()
}

def userUpdated() {
  log.debug "Updated with settings: ${settings}"
  userInitialize()
}

def userInitialize() {
  // reset listeners
  unsubscribe()
  unschedule()

  // setup data
  initializeLockData()
  initializeLocks()

  // set listeners
  subscribeToSchedule()
}

def userUninstalled() {
  unschedule()

  // prompt locks to delete this user
  initializeLocks()
}

def subscribeToSchedule() {
  if (startTime) {
    // sechedule time of start!
    log.debug 'scheduling time start'
    schedule(startTime, 'scheduledStartTime')
  }
  if (endTime) {
    // sechedule time of end!
    log.debug 'scheduling time end'
    schedule(endTime, 'scheduledEndTime')
  }
  if (startDateTime()) {
    // schedule calendar start!
    log.debug 'scheduling calendar start'
    runOnce(startDateTime().format(smartThingsDateFormat(), timeZone()), 'calendarStart')
  }
  if (endDateTime()) {
    // schedule calendar end!
    log.debug 'scheduling calendar end'
    runOnce(endDateTime().format(smartThingsDateFormat(), timeZone()), 'calendarEnd')
  }
}

def scheduledStartTime() {
  parent.setAccess()
}
def scheduledEndTime() {
  parent.setAccess()
}
def calendarStart() {
  parent.setAccess()
  if (calStartPhrase) {
    location.helloHome.execute(calStartPhrase)
  }
}
def calendarEnd() {
  parent.setAccess()
  if (calEndPhrase) {
    location.helloHome.execute(calEndPhrase)
  }
}

def initializeLockData() {
  debugger('Initialize lock data for user.')
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    def lockId = lockApp.lock.id
    if (state."lock${lockId}" == null) {
      state."lock${lockId}" = [:]
      state."lock${lockId}".enabled = true
      state."lock${lockId}".usage = 0
    }
  }
}

def initializeLocks() {
  debugger('User asking for lock init')
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    lockApp.queSetupLockData()
  }
}

def incrementLockUsage(lockId) {
  // this is called by a lock app when this user
  // used their code to lock the door
  state."lock${lockId}".usage = state."lock${lockId}".usage + 1
}

def lockReset(lockId) {
  state."lock${lockId}".enabled = true
  state."lock${lockId}".disabledReason = ''
  def lockApp = parent.getLockAppById(lockId)
  lockApp.enableUser(userSlot)
}

def userLandingPage() {
  if (userName) {
    userMainPage()
  } else {
    userSetupPage()
  }
}

def userSetupPage() {
  dynamicPage(name: 'userSetupPage', title: 'Setup Lock', nextPage: 'userMainPage', uninstall: true) {
    section('Choose details for this user') {
      input(name: 'userName', type: 'text', title: 'Name for User', required: true)
      input(name: 'userCode', type: 'text', title: userCodeInputTitle(), required: false, defaultValue: settings.'userCode', refreshAfterSelection: true)
      input(name: 'userSlot', type: 'enum', options: parent.availableSlots(settings.userSlot), title: 'Select slot', required: true, refreshAfterSelection: true )
    }
  }
}

def userMainPage() {
  //reset errors on each load
  dynamicPage(name: 'userMainPage', title: '', install: true, uninstall: true) {
    section('User Settings') {
      def usage = getAllLocksUsage()
      def text
      if (isActive()) {
        text = 'active'
      } else {
        text = 'inactive'
      }
      paragraph "${text}/${usage}"
      input(name: 'userCode', type: 'text', title: userCodeInputTitle(), required: false, defaultValue: settings.'userCode', refreshAfterSelection: true)
      input(name: 'userEnabled', type: 'bool', title: "User Enabled?", required: false, defaultValue: true, refreshAfterSelection: true)
    }
    section('Additional Settings') {
      def actions = location.helloHome?.getPhrases()*.label
      if (actions) {
        actions.sort()
        input name: 'userUnlockPhrase', type: 'enum', title: 'Hello Home Phrase on unlock', multiple: true, required: false, options: actions, refreshAfterSelection: true, image: 'http://images.lockmanager.io/app/v1/images/home.png'
        input name: 'userLockPhrase', type: 'enum', title: 'Hello Home Phrase on lock', description: 'Available on select locks only', multiple: true, required: false, options: actions, refreshAfterSelection: true, image: 'http://images.lockmanager.io/app/v1/images/home.png'

        input "userNoRunPresence", "capability.presenceSensor", title: "DO NOT run Actions if any of these are present:", multiple: true, required: false
        input "userDoRunPresence", "capability.presenceSensor", title: "ONLY run Actions if any of these are present:", multiple: true, required: false
      }
      input(name: 'burnAfterInt', title: 'How many uses before burn?', type: 'number', required: false, description: 'Blank or zero is infinite', image: 'http://images.lockmanager.io/app/v1/images/fire.png')
      href(name: 'toSchedulingPage', page: 'schedulingPage', title: 'Schedule (optional)', description: schedulingHrefDescription(), state: schedulingHrefDescription() ? 'complete' : '', image: 'http://images.lockmanager.io/app/v1/images/calendar.png')
      href(name: 'toNotificationPage', page: 'userNotificationPage', title: 'Notification Settings', description: userNotificationPageDescription(), state: userNotificationPageDescription() ? 'complete' : '', image: 'http://images.lockmanager.io/app/v1/images/bullhorn.png')
      href(name: 'toUserKeypadPage', page: 'userKeypadPage', title: 'Keypad Routines (optional)', image: 'http://images.lockmanager.io/app/v1/images/keypad.png')
    }
    section('Locks') {
      initializeLockData()
      def lockApps = parent.getLockApps()

      lockApps.each { app ->
        href(name: "toLockPage${app.lock.id}", page: 'userLockPage', params: [id: app.lock.id], description: lockPageDescription(app.lock.id), required: false, title: app.lock.label, image: lockPageImage(app.lock) )
      }
    }
    section('Setup', hideable: true, hidden: true) {
      label(title: "Name for App", defaultValue: 'User: ' + userName, required: true, image: 'http://images.lockmanager.io/app/v1/images/user.png')
      input name: 'userName', type: "text", title: "Name for user", required: true, image: 'http://images.lockmanager.io/app/v1/images/user.png'
      input(name: "userSlot", type: "enum", options: parent.availableSlots(settings.userSlot), title: "Select slot", required: true, refreshAfterSelection: true )
    }
  }
}

def userCodeInputTitle() {
  def title = 'Code 4-8 digits'
  def pinLength
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    pinLength = lockApp.pinLength()
    if (pinLength) {
      title = "Code (Must be ${lockApp.lock.latestValue('pinLength')} digits)"
    }
  }
  return title
}

def lockPageImage(lock) {
  if (!state."lock${lock.id}".enabled || settings."lockDisabled${lock.id}") {
    return 'http://images.lockmanager.io/app/v1/images/ban.png'
  } else {
    return 'http://images.lockmanager.io/app/v1/images/lock.png'
  }
}

def lockInfoPageImage(lock) {
  if (!state."lock${lock.id}".enabled || settings."lockDisabled${lock.id}") {
    return 'http://images.lockmanager.io/app/v1/images/user-times.png'
  } else {
    return 'http://images.lockmanager.io/app/v1/images/user.png'
  }
}

def userLockPage(params) {
  dynamicPage(name:"userLockPage", title:"Lock Settings") {
    debugger('current params: ' + params)
    def lock = getLock(params)
    def lockApp = parent.getLockAppById(lock.id)
    def slotData = lockApp.slotData(userSlot)

    def usage = state."lock${lock.id}".usage

    debugger('found lock id?: ' + lock?.id)

    if (!state."lock${lock.id}".enabled) {
      section {
        paragraph "WARNING:\n\nThis user has been disabled.\n${state."lock${lock.id}".disabledReason}", image: 'http://images.lockmanager.io/app/v1/images/ban.png'
        href(name: 'toReEnableUserLockPage', page: 'reEnableUserLockPage', title: 'Reset User', description: 'Retry setting this user.',  params: [id: lock.id], image: 'http://images.lockmanager.io/app/v1/images/refresh.png' )
      }
    }
    section("${deviceLabel(lock)} settings for ${app.label}") {
      if (slotData.code) {
        paragraph "Lock is currently set to ${slotData.code}"
      }
      paragraph "User unlock count: ${usage}"
      if(slotData.attempts > 0) {
        paragraph "Lock set failed try ${slotData.attempts}/10"
      }
      input(name: "lockDisabled${lock.id}", type: 'bool', title: 'Disable lock for this user?', required: false, defaultValue: settings."lockDisabled${lock.id}", refreshAfterSelection: true, image: 'http://images.lockmanager.io/app/v1/images/ban.png' )
      href(name: 'toLockResetPage', page: 'lockResetPage', title: 'Reset Lock', description: 'Reset lock data for this user.',  params: [id: lock.id], image: 'http://images.lockmanager.io/app/v1/images/refresh.png' )
    }
  }
}

def userKeypadPage() {
  dynamicPage(name: 'userKeypadPage',title: 'Keypad Settings (optional)', install: true, uninstall: true) {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section("Settings") {
      paragraph 'settings here are for this user only. When this user enters their passcode, run these routines'
      input(name: 'armRoutine', title: 'Arm/Away routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'disarmRoutine', title: 'Disarm routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'stayRoutine', title: 'Arm/Stay routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'nightRoutine', title: 'Arm/Night routine', type: 'enum', options: actions, required: false, multiple: true)
    }
  }
}

def lockPageDescription(lock_id) {
  def usage = state."lock${lock_id}".usage
  def description = "Entries: ${usage} "
  if (!state."lock${lock_id}".enabled) {
    description += '// ERROR//DISABLED'
  }
  if (settings."lockDisabled${lock_id}") {
    description += ' DISABLED'
  }
  description
}

def reEnableUserLockPage(params) {
  // do reset
  def lock = getLock(params)
  lockReset(lock.id)

  dynamicPage(name:'reEnableUserLockPage', title:'User re-enabled') {
    section {
      paragraph 'Lock has been reset.'
    }
    section {
      href(name: 'toMainPage', title: 'Back To Setup', page: 'userMainPage')
    }
  }
}

def lockResetPage(params) {
  // do reset
  def lock = getLock(params)

  state."lock${lock.id}".usage = 0
  lockReset(lock.id)

  dynamicPage(name:'lockResetPage', title:'Lock reset') {
    section {
      paragraph 'Lock has been reset.'
    }
    section {
      href(name: 'toMainPage', title: 'Back To Setup', page: 'userMainPage')
    }
  }
}

def schedulingPage() {
  dynamicPage(name: 'schedulingPage', title: 'Rules For Access Scheduling') {

    section {
      href(name: 'toCalendarPage', title: 'Calendar', page: 'calendarPage', description: calendarHrefDescription(), state: calendarHrefDescription() ? 'complete' : '')
    }

    section {
      input(name: 'days', type: 'enum', title: 'Allow User Access On These Days', description: 'Every day', required: false, multiple: true, options: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'], submitOnChange: true)
    }
    section {
      input(name: 'activeModes', title: 'Allow Access only when in any of these modes', type: 'mode', required: false, multiple: true, submitOnChange: true)
    }
    section {
      input(name: 'startTime', type: 'time', title: 'Start Time', description: null, required: false)
      input(name: 'endTime', type: 'time', title: 'End Time', description: null, required: false)
    }
  }
}

def calendarPage() {
  dynamicPage(name: "calendarPage", title: "Calendar Access") {
    section() {
      paragraph "Enter each field carefully."
    }
    def actions = location.helloHome?.getPhrases()*.label
    section("Start Date") {
      input name: "startDay", type: "number", title: "Day", required: false
      input name: "startMonth", type: "number", title: "Month", required: false
      input name: "startYear", type: "number", description: "Format(yyyy)", title: "Year", required: false
      input name: "calStartTime", type: "time", title: "Start Time", description: null, required: false
      if (actions) {
        actions.sort()
        input name: "calStartPhrase", type: "enum", title: "Hello Home Phrase", multiple: true, required: false, options: actions, refreshAfterSelection: true
      }
    }
    section("End Date") {
      input name: "endDay", type: "number", title: "Day", required: false
      input name: "endMonth", type: "number", title: "Month", required: false
      input name: "endYear", type: "number", description: "Format(yyyy)", title: "Year", required: false
      input name: "calEndTime", type: "time", title: "End Time", description: null, required: false
      if (actions) {
        actions.sort()
        input name: "calEndPhrase", type: "enum", title: "Hello Home Phrase", multiple: true, required: false, options: actions, refreshAfterSelection: true
      }
    }
  }
}

def userNotificationPage() {
  dynamicPage(name: 'userNotificationPage', title: 'Notification Settings') {

    section {
      if (phone == null && !notification && !recipients) {
        input(name: 'muteUser', title: 'Mute this user?', type: 'bool', required: false, submitOnChange: true, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally', image: 'http://images.lockmanager.io/app/v1/images/bell-slash-o.png')
      }
      if (!muteUser) {
        input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true, image: 'http://images.lockmanager.io/app/v1/images/book.png')
        href(name: 'toAskAlexaPage', title: 'Ask Alexa', page: 'userAskAlexaPage', image: 'http://images.lockmanager.io/app/v1/images/Alexa.png')
        if (!recipients) {
          input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
          paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
          input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
        }
        if (phone != null || notification || recipients) {
          input(name: 'notifyAccess', title: 'on User Entry', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/unlock-alt.png')
          input(name: 'notifyLock', title: 'on Lock', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/lock.png')
          input(name: 'notifyAccessStart', title: 'when granting access', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/check-circle-o.png')
          input(name: 'notifyAccessEnd', title: 'when revoking access', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/times-circle-o.png')
        }
      }
    }
    if (!muteUser) {
      section('Only During These Times (optional)') {
        input(name: 'notificationStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
        input(name: 'notificationEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
      }
    }
  }
}

def userAskAlexaPage() {
  dynamicPage(name: 'userAskAlexaPage', title: 'Ask Alexa Message Settings') {
    section('Que Messages with the Ask Alexa app') {
      input(name: 'alexaAccess', title: 'on User Entry', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/unlock-alt.png')
      input(name: 'alexaLock', title: 'on Lock', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/lock.png')
      input(name: 'alexaAccessStart', title: 'when granting access', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/check-circle-o.png')
      input(name: 'alexaAccessEnd', title: 'when revoking access', type: 'bool', required: false, image: 'http://images.lockmanager.io/app/v1/images/times-circle-o.png')
    }
    section('Only During These Times (optional)') {
      input(name: 'alexaStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
      input(name: 'alexaEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
    }
  }
}

def timeZone() {
  def zone
  if(location.timeZone) {
    zone = location.timeZone
  } else {
    zone = TimeZone.getDefault()
  }
  return zone
}

public smartThingsDateFormat() { "yyyy-MM-dd'T'HH:mm:ss.SSSZ" }

public humanReadableStartDate() {
  new Date().parse(smartThingsDateFormat(), startTime).format('h:mm a', timeZone(startTime))
}
public humanReadableEndDate() {
  new Date().parse(smartThingsDateFormat(), endTime).format('h:mm a', timeZone(endTime))
}

def readableDateTime(date) {
  new Date().parse(smartThingsDateFormat(), date.format(smartThingsDateFormat(), timeZone())).format("EEE, MMM d yyyy 'at' h:mma", timeZone())
}


def getLockUsage(lock_id) {
  return state."lock${lock_id}".usage
}

def getAllLocksUsage() {
  def usage = 0
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    if (state."lock${lockApp.lock.id}"?.usage) {
      usage = usage + state."lock${lockApp.lock.id}".usage
    }
  }
  return usage
}

def calendarHrefDescription() {
  def dateStart = startDateTime()
  def dateEnd = endDateTime()
  if (dateEnd && dateStart) {
    def startReadableTime = readableDateTime(dateStart)
    def endReadableTime = readableDateTime(dateEnd)
    return "Accessible from ${startReadableTime} until ${endReadableTime}"
  } else if (!dateEnd && dateStart) {
    def startReadableTime = readableDateTime(dateStart)
    return "Accessible on ${startReadableTime}"
  } else if (dateEnd && !dateStart){
    def endReadableTime = readableDateTime(dateEnd)
    return "Accessible until ${endReadableTime}"
  }
}

def userNotificationPageDescription() {
  def parts = []
  def msg = ''
  if (settings.phone) {
    parts << "SMS to ${phone}"
  }
  if (settings.notification) {
    parts << 'Push Notification'
  }
  if (settings.recipients) {
    parts << 'Sent to Address Book'
  }
  if (parts.size()) {
    msg += fancyString(parts)
  }
  parts = []

  if (settings.notifyAccess) {
    parts << 'on entry'
  }
  if (settings.notifyLock) {
    parts << 'on lock'
  }
  if (settings.notifyAccessStart) {
    parts << 'when granting access'
  }
  if (settings.notifyAccessEnd) {
    parts << 'when revoking access'
  }
  if (settings.notificationStartTime) {
    parts << "starting at ${settings.notificationStartTime}"
  }
  if (settings.notificationEndTime) {
    parts << "ending at ${settings.notificationEndTime}"
  }
  if (parts.size()) {
    msg += ': '
    msg += fancyString(parts)
  }
  if (muteUser) {
    msg = 'User Muted'
  }
  return msg
}

def deviceLabel(device) {
  return device.label ?: device.name
}

def schedulingHrefDescription() {
  def descriptionParts = []
  if (startDateTime() || endDateTime()) {
    descriptionParts << calendarHrefDescription()
  }
  if (days) {
    descriptionParts << "On ${fancyString(days)},"
  }
  if ((andOrTime != null) || (activeModes == null)) {
    if (startTime) {
      descriptionParts << "at ${humanReadableStartDate()}"
    }
    if (endTime) {
      descriptionParts << "until ${humanReadableEndDate()}"
    }
  }
  if (activeModes) {
    descriptionParts << "and when ${location.name} enters any of '${activeModes}' modes"
  }
  if (descriptionParts.size() <= 1) {
    // locks will be in the list no matter what. No rules are set if only locks are in the list
    return null
  }
  return descriptionParts.join(" ")
}

def isActive(lockId) {
  if (
      isUserEnabled() &&
      isValidCode() &&
      isNotBurned() &&
      isEnabled(lockId) &&
      userLockEnabled(lockId) &&
      isCorrectDay() &&
      isInCalendarRange() &&
      isCorrectMode() &&
      isInScheduledTime()
     ) {
    return true
  } else {
    return false
  }
}

def isActiveKeypad() {
  if (
      isUserEnabled() &&
      isValidCode() &&
      isNotBurned() &&
      isCorrectDay() &&
      isInCalendarRange() &&
      isCorrectMode() &&
      isInScheduledTime()
     ) {
    return true
  } else {
    return false
  }
}

def isUserEnabled() {
	if (userEnabled == null || userEnabled) {  //If true or unset, return true
		return true
	} else {
		return false
	}
}

def isValidCode() {
  if (userCode?.isNumber()) {
    return true
  } else {
    return false
  }
}

def isNotBurned() {
  if (burnAfterInt == null || burnAfterInt == 0) {
    return true // is not a burnable user
  } else {
    def totalUsage = getAllLocksUsage()
    if (totalUsage >= burnAfterInt) {
      // usage number is met!
      return false
    } else {
      // dont burn this user yet
      return true
    }
  }
}

def isEnabled(lockId) {
  if (state."lock${lockId}" == null) {
    return true
  } else if (state."lock${lockId}".enabled == null) {
    return true
  } else {
    return state."lock${lockId}".enabled
  }
}

def userLockEnabled(lockId) {
  def lockDisabled = settings."lockDisabled${lockId}"
  if (lockDisabled == null) {
    return true
  } else if (lockDisabled == true) {
    return false
  } else {
    return true
  }
}

def isCorrectDay() {
  def today = new Date().format("EEEE", timeZone())
  if (!days || days.contains(today)) {
    // if no days, assume every day
    return true
  }
  return false
}

def isInCalendarRange() {
  def dateStart = startDateTime()
  def dateEnd = endDateTime()
  def now = rightNow().getTime()
  if (dateStart && dateEnd) {
    // There's both an end time, and a start time.  Allow access between them.
    if (dateStart.getTime() < now && dateEnd.getTime() > now) {
      // It's in calendar times
      return true
    } else {
      // It's not in calendar times
      return false
    }
  } else if (dateEnd && !dateStart) {
    // There's a end time, but no start time.  Allow access until end
    if (dateStart.getTime() > now) {
      // It's after the start time
      return true
    } else {
      // It's before the start time
      return false
    }
  } else if (!dateEnd && dateStart) {
    // There's a start time, but no end time.  Allow access after start
    if (dateStart.getTime() < now) {
      // It's after the start time
      return true
    } else {
      // It's before the start time
      return false
    }
  } else {
    // there's no calendar
    return true
  }
}

def isCorrectMode() {
  if (activeModes) {
    // mode check is on
    if (activeModes.contains(location.mode)) {
      // we're in a right mode
      return true
    } else {
      // we're in the wrong mode
      return false
    }
  } else {
    // mode check is off
    return true
  }
}

def isInScheduledTime() {
  def now = new Date()
  if (startTime && endTime) {
    def start = timeToday(startTime)
    def stop = timeToday(endTime)

    // there's both start time and end time
    if (start.before(now) && stop.after(now)){
      // It's between the times
      return true
    } else {
      // It's not between the times
      return false
    }
  } else if (startTime && !endTime){
    // there's a start time, but no end time
    def start = timeToday(startTime)
    if (start.before(now)) {
      // it's after start time
      return true
    } else {
      //it's before start time
      return false
    }
  } else if (!startTime && endTime) {
    // there's an end time but no start time
    def stop = timeToday(endTime)
    if (stop.after(now)) {
      // it's still before end time
      return true
    } else {
      // it's after end time
      return false
    }
  } else {
    // there are no times
    return true
  }
}

def startDateTime() {
  if (startDay && startMonth && startYear && calStartTime) {
    def time = new Date().parse(smartThingsDateFormat(), calStartTime).format("'T'HH:mm:ss.SSSZ", timeZone(calStartTime))
    return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "${startYear}-${startMonth}-${startDay}${time}")
  } else {
    // Start Date Time not set
    return false
  }
}

def endDateTime() {
  if (endDay && endMonth && endYear && calEndTime) {
    def time = new Date().parse(smartThingsDateFormat(), calEndTime).format("'T'HH:mm:ss.SSSZ", timeZone(calEndTime))
    return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "${endYear}-${endMonth}-${endDay}${time}")
  } else {
    // End Date Time not set
    return false
  }
}

def rightNow() {
  def now = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", timeZone())
  return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", now)
}

def getLockById(params) {
  return parent.locks.find{it.id == id}
}

def getLock(params) {
  def id = ''
  // Assign params to id.  Sometimes parameters are double nested.
  debugger('params: ' + params)
  debugger('last: ' + state.lastLock)
  if (params?.id) {
    id = params.id
  } else if (params?.params){
    id = params.params.id
  }
  def lockApp = parent.getLockAppById(id)
  if (!lockApp) {
    lockApp = parent.getLockAppById(state.lastLock)
  }

  if (lockApp) {
    state.lastLock = lockApp.lock.id
    return lockApp.lock
  } else {
    return false
  }
}

def userNotificationSettings() {
  def userSettings = false
  if (phone != null || notification || muteUser || recipients) {
    // user has it's own settings!
    userSettings = true
  }
  return userSettings
}

def sendUserMessage(msg) {
  if (userNotificationSettings()) {
    checkIfNotifyUser(msg)
  } else {
    checkIfNotifyGlobal(msg)
  }
}

def checkIfNotifyUser(msg) {
  if (notificationStartTime != null && notificationEndTime != null) {
    def start = timeToday(notificationStartTime)
    def stop = timeToday(notificationEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendMessageViaUser(msg)
    }
  } else {
    sendMessageViaUser(msg)
  }
}

def checkIfNotifyGlobal(msg) {
  if (parent.notificationStartTime != null && parent.notificationEndTime != null) {
    def start = timeToday(parent.notificationStartTime)
    def stop = timeToday(parent.notificationEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendMessageViaParent(msg)
    }
  } else {
    sendMessageViaParent(msg)
  }
}

def sendMessageViaParent(msg) {
  if (parent.recipients) {
    sendNotificationToContacts(msg, parent.recipients)
  } else {
    if (parent.notification) {
      sendPush(msg)
    } else {
      sendNotificationEvent(msg)
    }
    if (parent.phone) {
      if ( parent.phone.indexOf(";") > 1){
        def phones = parent.phone.split(";")
        for ( def i = 0; i < phones.size(); i++) {
          sendSms(phones[i], msg)
        }
      }
      else {
        sendSms(parent.phone, msg)
      }
    }
  }
}

def sendMessageViaUser(msg) {
  if (recipients) {
    sendNotificationToContacts(msg, recipients)
  } else {
    if (notification) {
      sendPush(msg)
    } else {
      sendNotificationEvent(msg)
    }
    if (phone) {
      if ( phone.indexOf(";") > 1){
        def phones = phone.split(";")
        for ( def i = 0; i < phones.size(); i++) {
          sendSms(phones[i], msg)
        }
      }
      else {
        sendSms(phone, msg)
      }
    }
  }
}

def disableAndSetReason(lockID, reason) {
  state."lock${lockID}".enabled = false
  state."lock${lockID}".disabledReason = reason
}

def disableLock(lockID) {
  state."lock${lockID}".enabled = false
  state."lock${lockID}".disabledReason = 'Controller failed to set user code.'
}

def enableLock(lockID) {
  state."lock${lockID}".enabled = true
  state."lock${lockID}".disabledReason = null
}

def disabledReason() {
  state."lock${lockID}".disabledReason
}

def getLockUserInfo(lock) {
  def para = "\n${app.label}"
  if (settings."lockDisabled${lock.id}") {
    para += " DISABLED"
  }
  def usage = state."lock${lock.id}".usage
  para += " // Entries: ${usage}"
  if (!state."lock${lock.id}".enabled) {
    def reason = state."lock${lock.id}".disabledReason
    para += "\n ${reason}"
  }
  para
}

// User Ask Alexa

def userAlexaSettings() {
  if (alexaAccess || alexaLock || alexaAccessStart || alexaAccessEnd || alexaStartTime || alexaEndTime) {
    // user has it's own settings!
    return true
  }
  // user doesn't !
  return false
}

def askAlexaUser(msg) {
  if (userAlexaSettings()) {
    checkIfAlexaUser(msg)
  } else {
    checkIfAlexaGlobal(msg)
  }
}

def checkIfAlexaUser(message) {
  if (!muteUser) {
    if (alexaStartTime != null && alexaEndTime != null) {
      def start = timeToday(alexaStartTime)
      def stop = timeToday(alexaEndTime)
      def now = new Date()
      if (start.before(now) && stop.after(now)){
        sendAskAlexaUser(message)
      }
    } else {
      sendAskAlexaUser(message)
    }
  }
}

def checkIfAlexaGlobal(message) {
  if (parent.alexaStartTime != null && parent.alexaEndTime != null) {
    def start = timeToday(parent.alexaStartTime)
    def stop = timeToday(parent.alexaEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendAskAlexaUser(message)
    }
  } else {
    sendAskAlexaUser(message)
  }
}

def sendAskAlexaUser(message) {
  sendLocationEvent(name: 'AskAlexaMsgQueue',
                    value: 'LockManager/User',
                    isStateChange: true,
                    descriptionText: message,
                    unit: "User//${userName}")
}