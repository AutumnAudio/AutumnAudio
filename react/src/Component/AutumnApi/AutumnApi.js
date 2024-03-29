export function joinRoom(genre) {
    return fetch('/joinroom/' + genre, {
        method: 'POST'
    })
}
export function getChatrooms() {
    return fetch('/chatrooms')
}
export function leaveRoom(genre) {
    return fetch('/leaveroom/' + genre, {
        method: 'DELETE'
    })
}
export function sendChat(text) {
    let formData = new FormData();
    formData.append('text', text)
    return fetch('/send', {
        body: formData,
        method: 'POST'
    })
}
export function authenticate() {
    return fetch('/auth')
}

export function shareSong() {
    console.log('share')
    let formData = new FormData()
    return fetch('/share', {
        body: formData,
        method: 'POST'
    })
}

export function addSong(uri) {
    let formData = new FormData()
    formData.append('uri', uri)
    return fetch('/add', {
        body: formData,
        method: 'POST'
    })
}