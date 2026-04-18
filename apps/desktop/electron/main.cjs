const { app, BrowserWindow } = require('electron')

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    title: 'Brasil Tupi Conecta',
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
    },
  })

  function tryLoad() {
    win.loadURL('http://localhost:5173').catch(() => {
      setTimeout(tryLoad, 1000)
    })
  }

  setTimeout(tryLoad, 5000)
}

app.whenReady().then(createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})