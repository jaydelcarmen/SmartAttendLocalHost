class SignupPage {
    constructor() {
        document.getElementById('registerForm').addEventListener('submit', e => {
            e.preventDefault();
            this.validateRegister();
        });
    }

    RegisterTogglePass() {
        this._toggleEye('RegisterPassword', 'RegisterEyeIcon');
    }

    ConfirmPasswordToggle() {
        this._toggleEye('ConfirmPassword', 'ConfirmEyeIcon');
    }

    _toggleEye(inputId, iconId) {
        const input   = document.getElementById(inputId);
        const icon    = document.getElementById(iconId);
        const isHidden = input.type === 'password';
        input.type = isHidden ? 'text' : 'password';
        icon.classList.toggle('fa-eye', !isHidden);
        icon.classList.toggle('fa-eye-slash', isHidden);
    }

    async checkEmailExists(email) {
        try {
            const res = await fetch(`/teacher/check-email?email=${email}`, { credentials: 'include' });
            if (res.status === 400) return true;
            if (res.ok) return false;
            console.error('Unexpected server response:', res.status, await res.text());
            return true;
        } catch (err) {
            console.error('Error checking email:', err);
            return true;
        }
    }

    async validateRegister() {
        const password        = document.getElementById('RegisterPassword').value;
        const confirmPassword = document.getElementById('ConfirmPassword').value;
        const email           = document.getElementById('register_email').value;
        const name            = document.getElementById('register_name').value;

        if (!password || !confirmPassword || !email || !name)
            return Swal.fire({ icon: 'warning', title: 'Missing Fields', text: 'Please fill up the form' });

        if (!this._isValidEmail(email))
            return Swal.fire({ icon: 'error', title: 'Invalid Email', text: 'Please enter valid email address' });

        if (await this.checkEmailExists(email))
            return Swal.fire({ icon: 'error', title: 'Email already exists', text: 'Please use different email address' });

        if (password.length < 8)
            return Swal.fire({ icon: 'warning', title: 'Warning', text: 'Password must be at least 8 characters', confirmButtonColor: '#4e73df' });

        if (!/[^A-Za-z0-9]/.test(password))
            return Swal.fire({ icon: 'warning', title: 'Warning', text: 'Password must contain at least one special character', confirmButtonColor: '#4e73df' });

        if (password !== confirmPassword)
            return Swal.fire({ icon: 'error', title: 'Error', text: 'Password do not match!' });

        try {
            const res = await fetch('/teacher/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, email, password }),
                credentials: 'include'
            });

            if (res.ok) {
                await Swal.fire({ icon: 'success', title: 'Success', text: 'Your account has been created successfully! Wait for admin approval.', timer: 3000, showConfirmButton: false });
                document.getElementById('registerForm').reset();
            } else {
                Swal.fire({ icon: 'error', title: 'Error', text: await res.text() || 'Something went wrong' });
            }
        } catch (err) {
            console.error('Error submitting teacher:', err);
            Swal.fire({ icon: 'error', title: 'Error', text: 'Unable to connect to the server' });
        }
    }

    _isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }
}

const signupPage = new SignupPage();

// Global wrappers for HTML onclick attributes
function RegisterTogglePass()    { signupPage.RegisterTogglePass(); }
function ConfirmPasswordToggle() { signupPage.ConfirmPasswordToggle(); }
