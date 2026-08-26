import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';

type AuthMode = 'signIn' | 'signUp' | 'forgotPassword' | 'updatePassword';

@Component({
  selector: 'sf-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  mode: AuthMode = 'signIn';
  fullName = '';
  email = '';
  password = '';
  confirmPassword = '';
  submitting = false;
  error = '';
  message = '';

  constructor(readonly auth: AuthService) {
    if (auth.passwordRecovery()) this.mode = 'updatePassword';
  }

  setMode(mode: AuthMode): void {
    this.mode = mode;
    this.error = '';
    this.message = '';
    this.password = '';
    this.confirmPassword = '';
  }

  title(): string {
    if (this.mode === 'signUp') return 'Create your account';
    if (this.mode === 'forgotPassword') return 'Reset your password';
    if (this.mode === 'updatePassword') return 'Choose a new password';
    return 'Welcome back';
  }

  description(): string {
    if (this.mode === 'signUp') return 'Use your work email to create a StockFlow account.';
    if (this.mode === 'forgotPassword') return 'Enter your account email and we will send you a secure reset link.';
    if (this.mode === 'updatePassword') return 'Create a new password for your StockFlow account.';
    return 'Sign in to access your StockFlow workspace.';
  }

  submitLabel(): string {
    if (this.mode === 'signUp') return 'Create account';
    if (this.mode === 'forgotPassword') return 'Send reset link';
    if (this.mode === 'updatePassword') return 'Update password';
    return 'Sign in to StockFlow';
  }

  async submit(): Promise<void> {
    if (this.submitting) return;

    const email = this.email.trim().toLowerCase();
    if (this.mode !== 'updatePassword' && !email) {
      this.error = 'Enter your email address.';
      return;
    }
    if (this.mode !== 'updatePassword' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.error = 'Enter a valid email address, for example name@company.com.';
      return;
    }
    if (this.mode !== 'forgotPassword' && !this.password) {
      this.error = 'Enter your password.';
      return;
    }
    if (this.mode === 'signUp' && !this.fullName.trim()) {
      this.error = 'Enter your full name.';
      return;
    }
    if ((this.mode === 'signUp' || this.mode === 'updatePassword') && this.password.length < 8) {
      this.error = 'Use a password with at least 8 characters.';
      return;
    }
    if (this.mode === 'updatePassword' && this.password !== this.confirmPassword) {
      this.error = 'The passwords do not match.';
      return;
    }

    this.submitting = true;
    this.error = '';
    this.message = '';

    try {
      if (this.mode === 'signIn') {
        this.error = await this.auth.signIn(email, this.password) ?? '';
        return;
      }

      if (this.mode === 'forgotPassword') {
        this.error = await this.auth.requestPasswordReset(email) ?? '';
        if (!this.error) {
          this.message = 'If an account exists for that email, a password reset link has been sent.';
        }
        return;
      }

      if (this.mode === 'updatePassword') {
        this.error = await this.auth.updatePassword(this.password) ?? '';
        if (this.error) return;

        const signOutError = await this.auth.signOut();
        if (signOutError) {
          this.error = 'Your password was updated, but automatic sign-out failed. Refresh and sign in again.';
          return;
        }

        this.auth.finishPasswordRecovery();
        this.mode = 'signIn';
        this.password = '';
        this.confirmPassword = '';
        this.message = 'Password updated successfully. Sign in with your new password.';
        return;
      }

      const result = await this.auth.signUp({
        email,
        password: this.password,
        fullName: this.fullName.trim()
      });
      this.error = result.error ?? '';
      if (!result.error && result.confirmationRequired) {
        this.message = 'Account created. Check your email to confirm your address, then sign in.';
        this.mode = 'signIn';
        this.password = '';
      }
    } catch {
      this.error = 'Authentication is temporarily unavailable. Please try again.';
    } finally {
      this.submitting = false;
    }
  }
}
