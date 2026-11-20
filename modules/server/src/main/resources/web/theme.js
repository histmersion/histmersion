// The reader's theme (the web UI's Theme, kept in localStorage), applied before the stylesheet paints so a page never
// flashes the other theme first; the stylesheet follows the system without it. A file of its own, not an inline
// script: the Content-Security-Policy runs no inline script.
try{var t=localStorage.getItem("histmersion.theme");if(t==="light"||t==="dark")document.documentElement.setAttribute("data-theme",t)}catch(e){}
