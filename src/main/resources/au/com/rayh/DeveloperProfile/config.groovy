package au.com.rayh.DeveloperProfile

f = namespace(lib.FormTagLib)

f.entry(title:_("Description"), field:"description") {
    if (instance!=null)
        raw("<input type=hidden name=id value=${instance.id}>");
    f.textbox()
}


def fileForm() {
    f.entry(title:_("*.developerprofile  File"), field:"image") {
        raw("<input type=file name=image size=40 jsonAware=yes>")
    }
    f.entry(title:_("Password"), field:"password") {
        f.password()
    }
}

if (instance.image==null) {// TODO: revisit
    fileForm()
} else {
    f.optionalBlock(title:_("Overwrite *.developerprofile File"),inline:true) {
        fileForm()
    }
}

/*
f.entry() {
    iframe(src:"${rootURL}/descriptor/${DeveloperProfile.class.name}/upload?id=TODO")
}
*/